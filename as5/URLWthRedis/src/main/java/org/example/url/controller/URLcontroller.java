package org.example.url.controller;


import org.example.url.service.URLService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping()
public class URLcontroller {
    @Autowired
    private URLService service;

    @PostMapping
    public String shorten(@RequestBody String url) {
        return service.shorten(url);
    }

    @GetMapping("/{url}")
//    public String getURL(@PathVariable String url) {
    public ResponseEntity<?> getURLwR(@PathVariable String url) {
        String ourl=service.findbyurlwr(url);

        HttpHeaders headers=new HttpHeaders();
        headers.add("Location",ourl);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
//            String originalUrl = service.findbyurl(url);
//
//            return "redirect:" + originalUrl;
    }
    @GetMapping("/db/{url}")
//    public String getURL(@PathVariable String url) {
    public ResponseEntity<?> getURL(@PathVariable String url) {
        String ourl=service.findbyurl(url);

        HttpHeaders headers=new HttpHeaders();
        headers.add("Location",ourl);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
//            String originalUrl = service.findbyurl(url);
//
//            return "redirect:" + originalUrl;
    }
    @PostMapping("/{alias}")
    public String shortenwithalias(@RequestBody String url,@PathVariable String alias) {

        return service.shorten(url,alias);
    }





}
