package com.jannchie.biliob.service.impl;

import com.jannchie.biliob.constant.ResultEnum;
import com.jannchie.biliob.model.Site;
import com.jannchie.biliob.service.SiteService;
import com.jannchie.biliob.utils.Result;
import com.mongodb.Block;
import com.mongodb.client.MongoClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

import static com.mongodb.client.model.Accumulators.max;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;

/**
 * @author jannchie
 */
@Service
public class SiteServiceImpl implements SiteService {

    private static final Integer MAX_ONLINE_PLAY_RANGE = 30;
    private static final Integer HOUR_IN_DAY = 24;

    private static final Logger logger = LogManager.getLogger(VideoServiceImpl.class);
    private final MongoTemplate mongoTemplate;
    private final MongoClient mongoClient;

    @Autowired
    public SiteServiceImpl(MongoTemplate mongoTemplate, MongoClient mongoClient) {
        this.mongoClient = mongoClient;

        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Get the data of the number of people watching video on bilibili.
     *
     * @param days The days of data that this method should return.
     * @return Online number result.
     */
    @Override
    public ResponseEntity listOnline(Integer days) {
        if (days > SiteServiceImpl.MAX_ONLINE_PLAY_RANGE) {
            return new ResponseEntity<>(new Result(ResultEnum.OUT_OF_RANGE), HttpStatus.BAD_REQUEST);
        }
        Integer limit = days * SiteServiceImpl.HOUR_IN_DAY;
        Query query = new Query();
        query.limit(limit).with(new Sort(Sort.Direction.DESC, "datetime"));
        List<Site> result = mongoTemplate.find(query, Site.class, "site_info");


        SiteServiceImpl.logger.info("获得全站在线播放数据");
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List> getGroupedOnlineData() {
        List<Map<String, Object>> group = new ArrayList<>();
        Block<Document> printBlock = m -> {
            Map<String, Object> map = new HashMap<>(m);
            group.add(map);
        };
        mongoClient.getDatabase("biliob").
                getCollection("site_info").
                aggregate(Arrays.asList(group(eq("$month", "$datetime"), max("datetime", "$datetime"), max("play_online", "$play_online"), max("web_online", "$web_online"), max("all_count", "$all_count")), sort(descending("datetime")), limit(12))).forEach(printBlock);

        return new ResponseEntity<>(group, HttpStatus.OK);
    }

    @Override
    @Cacheable(value = "biliob_counter")
    public Map getBiliOBCounter() {

        Map<String, Long> videoResult = getVideoCount();

        Map<String, Long> authorResult = getAuthorCount();

        Map<String, Long> userResult = getUserCount();

        Map<String, Object> result = new HashMap<>(3);
        result.put("video", videoResult);
        result.put("author", authorResult);
        result.put("user", userResult);

        Calendar c = Calendar.getInstance();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        Date time = c.getTime();
        result.put("updateTime", formatter.format(c.getTime()));

        SiteServiceImpl.logger.info("刷新全站数量统计");
        return result;
    }

    @Override
    public Map<String, Long> getUserCount() {
        Long userCount = mongoTemplate.count(new Query(), "user");
        Map<String, Long> userResult = new HashMap<>(1);
        userResult.put("count", userCount);
        return userResult;
    }

    @Override
    public Map<String, Long> getAuthorCount() {
        Long authorForceFocusCount =
                mongoTemplate.count(new Query(Criteria.where("forceFocus").is(true)), "author");
        Long authorFocusCount =
                mongoTemplate.count(
                        new Query(Criteria.where("focus").is(true).and("forceFocus").exists(false)), "author");
        Long authorNotFocusCount =
                mongoTemplate.count(
                        new Query(Criteria.where("focus").is(false).and("forceFocus").exists(false)), "author");
        Map<String, Long> authorResult = new HashMap<>(3);
        authorResult.put("forceFocusCount", authorForceFocusCount);
        authorResult.put("focusCount", authorFocusCount);
        authorResult.put("count", authorForceFocusCount + authorFocusCount + authorNotFocusCount);
        return authorResult;
    }

    @Override
    public Map<String, Long> getVideoCount() {
        Long videoFocusCount =
                mongoTemplate.count(new Query(Criteria.where("focus").is(true)), "video");
        Long videoNotFocusCount =
                mongoTemplate.count(new Query(Criteria.where("focus").is(false)), "video");
        Map<String, Long> videoResult = new HashMap<>(2);
        videoResult.put("focusCount", videoFocusCount);
        videoResult.put("count", videoFocusCount + videoNotFocusCount);
        return videoResult;
    }

    @Override
    public Map getAlert() {
        return mongoTemplate.findOne(new Query(), Map.class);
    }

    @Override
    public ResponseEntity postAlert() {
        return ResponseEntity.ok("");
//        return mongoTemplate.upsert();
    }
}
