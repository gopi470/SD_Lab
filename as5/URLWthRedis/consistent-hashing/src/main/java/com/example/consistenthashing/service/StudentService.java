package com.example.consistenthashing.service;

import com.example.consistenthashing.model.StorageNode;
import com.example.consistenthashing.model.Student;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StudentService {

    private final HashRing hashRing;
    private final Map<String, StorageNode> nodes = new HashMap<>();
    private final Map<String, MongoTemplate> templates = new HashMap<>();

    public StudentService(HashRing hashRing) {
        this.hashRing = hashRing;
        addInitialNode(new StorageNode("Node-1", "mongodb://localhost:27017/studentdb"));
        addInitialNode(new StorageNode("Node-2", "mongodb://localhost:27018/studentdb"));
        addInitialNode(new StorageNode("Node-3", "mongodb://localhost:27019/studentdb"));
    }

    private void addInitialNode(StorageNode node) {
        nodes.put(node.getName(), node);
        MongoTemplate template = new MongoTemplate(new SimpleMongoClientDatabaseFactory(node.getUri()));
        templates.put(node.getName(), template);
        hashRing.addNode(node);
    }

    public String save(Student student) {
        if (student == null || student.getId() == null || student.getId().isBlank()) {
            throw new IllegalArgumentException("Student ID is required");
        }
        StorageNode node = hashRing.getNode(student.getId());
        MongoTemplate template = templates.get(node.getName());
        template.save(student);
        return "Student stored in " + node.getName();
    }

    public Student find(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Student ID is required");
        }
        StorageNode node = hashRing.getNode(id);
        MongoTemplate template = templates.get(node.getName());
        return template.findById(id, Student.class);
    }

    public String addNode(StorageNode node) {
        if (nodes.containsKey(node.getName())) {
            return "Node already exists";
        }
        MongoTemplate template = new MongoTemplate(new SimpleMongoClientDatabaseFactory(node.getUri()));
        nodes.put(node.getName(), node);
        templates.put(node.getName(), template);
        hashRing.addNode(node);
        migrateRecords();
        return node.getName() + " added successfully";
    }

    public String removeNode(String nodeName) {
        StorageNode node = nodes.get(nodeName);
        if (node == null) {
            return "Node not found";
        }
        if (nodes.size() == 1) {
            return "Cannot remove the last node";
        }

        MongoTemplate oldTemplate = templates.get(nodeName);
        List<Student> students = oldTemplate.findAll(Student.class);
        hashRing.removeNode(node);
        nodes.remove(nodeName);
        templates.remove(nodeName);

        for (Student student : students) {
            StorageNode newNode = hashRing.getNode(student.getId());
            templates.get(newNode.getName()).save(student);
        }

        oldTemplate.dropCollection(Student.class);
        return nodeName + " removed successfully";
    }

    private void migrateRecords() {
        for (StorageNode node : nodes.values()) {
            MongoTemplate template = templates.get(node.getName());
            List<Student> students = template.findAll(Student.class);
            for (Student student : students) {
                StorageNode correctNode = hashRing.getNode(student.getId());
                if (!correctNode.getName().equals(node.getName())) {
                    templates.get(correctNode.getName()).save(student);
                    template.remove(student);
                }
            }
        }
    }

    public Map<String, Long> distribution() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (StorageNode node : nodes.values()) {
            long count = templates.get(node.getName()).count(
                    org.springframework.data.mongodb.core.query.Query.query(
                            org.springframework.data.mongodb.core.query.Criteria.where("id").exists(true)
                    ),
                    Student.class
            );
            result.put(node.getName(), count);
        }
        return result;
    }
}
