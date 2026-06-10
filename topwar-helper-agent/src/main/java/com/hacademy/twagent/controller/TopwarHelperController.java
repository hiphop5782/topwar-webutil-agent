package com.hacademy.twagent.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hacademy.twagent.service.ServerJsonSummaryService;
import com.hacademy.twagent.service.TopwarAnalyzeService;
import com.hacademy.twagent.vo.AnalyzeRequest;
import com.hacademy.twagent.vo.CacheKey;

import reactor.core.publisher.Flux;

@CrossOrigin
@RestController
@RequestMapping("/api/server")
public class TopwarHelperController {
	@Autowired
	private ServerJsonSummaryService serverJsonSummaryService;
	@Autowired
	private TopwarAnalyzeService topwarAnalyzeService;
	
	@GetMapping("/running")
	public String running() {
		return "Server is running";
	}
	
	@PostMapping(value = "/analyze", produces = MediaType.TEXT_PLAIN_VALUE)
	public Flux<String> analyze(@RequestBody AnalyzeRequest request) {
	    String lang = request.lang() == null || request.lang().isBlank()
	            ? "ko" : request.lang();
	    String optimizedJson = serverJsonSummaryService.summarizeForAi(request.json());
	    return topwarAnalyzeService.analyzeStream(CacheKey.builder()
	    			.lang(lang).server(request.server())
	    		.build(), request.time(), optimizedJson);
	}
}
