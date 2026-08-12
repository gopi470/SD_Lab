package com.example.consistenthashing.controller;

import com.example.consistenthashing.model.StorageNode;
import com.example.consistenthashing.model.Student;
import com.example.consistenthashing.service.StudentService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/students")
public class StudentController {

    private final StudentService service;

    public StudentController(StudentService service) {
        this.service = service;
    }

    @PostMapping
    public String addStudent(@RequestBody Student student) {
        return service.save(student);
    }

    @GetMapping("/{id}")
    public Student getStudent(@PathVariable String id) {
        return service.find(id);
    }

    @PostMapping("/nodes")
    public String addNode(@RequestBody StorageNode node) {
        return service.addNode(node);
    }

    @DeleteMapping("/nodes/{name}")
    public String removeNode(@PathVariable String name) {
        return service.removeNode(name);
    }

    @GetMapping("/distribution")
    public Map<String, Long> distribution() {
        return service.distribution();
    }
}
