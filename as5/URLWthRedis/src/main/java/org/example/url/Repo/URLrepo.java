    package org.example.url.Repo;

    import org.example.url.model.URLSh;
    import org.springframework.data.mongodb.repository.MongoRepository;

    public interface URLrepo extends MongoRepository<URLSh, String> {
        URLSh findBySurl(String surl);
    }
