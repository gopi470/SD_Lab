package org.example.url.service;

import org.example.url.Repo.URLrepo;
import org.example.url.model.URLSh;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class URLService {

    @Autowired
    private URLrepo repo;

    @Autowired
    private StringRedisTemplate redisTemplate;


    // Logger
    private static final Logger log =
            LoggerFactory.getLogger(URLService.class);


    // Counters
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong mongoQueries = new AtomicLong(0);


    // ------------------------------------------------
    // CREATE SHORT URL
    // ------------------------------------------------

    public String shorten(String URL) {

        String shortCode =
                UUID.randomUUID().toString().substring(0, 6);

        // Check whether generated code already exists
        while (repo.findBySurl(shortCode) != null) {

            shortCode =
                    UUID.randomUUID().toString().substring(0, 6);
        }

        URLSh url = new URLSh();

        url.setUrl(URL);
        url.setSurl(shortCode);

        repo.save(url);

        return shortCode;
    }


    // ------------------------------------------------
    // GET URL WITH REDIS
    // ------------------------------------------------

    public String findbyurlwr(String shortCode) {

        // Increase total request count
        totalRequests.incrementAndGet();


        // Check Redis
        String cachedUrl =
                redisTemplate
                        .opsForValue()
                        .get(shortCode);


        // -------------------------
        // CACHE HIT
        // -------------------------

        if (cachedUrl != null) {

            cacheHits.incrementAndGet();

            log.info(
                    "CACHE HIT | Total={} | Hits={} | Misses={} | MongoQueries={}",
                    totalRequests.get(),
                    cacheHits.get(),
                    cacheMisses.get(),
                    mongoQueries.get()
            );

            return cachedUrl;
        }


        // -------------------------
        // CACHE MISS
        // -------------------------

        cacheMisses.incrementAndGet();


        // Query MongoDB
        mongoQueries.incrementAndGet();

        URLSh url = repo.findBySurl(shortCode);


        if (url == null) {

            log.warn(
                    "URL NOT FOUND | shortCode={}",
                    shortCode
            );

            throw new RuntimeException(
                    "Short URL not found"
            );
        }


        String originalUrl = url.getUrl();


        // Store in Redis for 1 hour
        redisTemplate
                .opsForValue()
                .set(
                        shortCode,
                        originalUrl,
                        1,
                        TimeUnit.HOURS
                );


        log.info(
                "CACHE MISS | Total={} | Hits={} | Misses={} | MongoQueries={}",
                totalRequests.get(),
                cacheHits.get(),
                cacheMisses.get(),
                mongoQueries.get()
        );


        return originalUrl;
    }


    // ------------------------------------------------
    // GET URL WITHOUT REDIS
    // ------------------------------------------------

    public String findbyurl(String shortCode) {

        URLSh url =
                repo.findBySurl(shortCode);

        if (url == null) {

            throw new RuntimeException(
                    "Short URL not found"
            );
        }

        return url.getUrl();
    }


    // ------------------------------------------------
    // CREATE SHORT URL WITH CUSTOM ALIAS
    // ------------------------------------------------

    public String shorten(
            String url,
            String alias
    ) {

        String shortCode;


        // User provided alias
        if (alias != null && !alias.isBlank()) {


            // Check alias already exists
            if (repo.findBySurl(alias) != null) {

                throw new RuntimeException(
                        "Alias already exists"
                );
            }


            shortCode = alias;

        }

        // No alias provided
        else {

            shortCode =
                    UUID.randomUUID()
                            .toString()
                            .substring(0, 6);


            // Ensure uniqueness
            while (repo.findBySurl(shortCode) != null) {

                shortCode =
                        UUID.randomUUID()
                                .toString()
                                .substring(0, 6);
            }
        }


        URLSh urlObj = new URLSh();

        urlObj.setUrl(url);
        urlObj.setSurl(shortCode);

        repo.save(urlObj);


        return shortCode;
    }
}