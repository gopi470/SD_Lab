package org.example.url.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data

@Document()
public class URLSh {
    @Id
    private String id;
    private String url;
    private String surl;
}
