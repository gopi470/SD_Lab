package com.example.consistenthashing.model;

public class StorageNode {
    private String name;
    private String uri;

    public StorageNode() {}

    public StorageNode(String name, String uri) {
        this.name = name;
        this.uri = uri;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}
