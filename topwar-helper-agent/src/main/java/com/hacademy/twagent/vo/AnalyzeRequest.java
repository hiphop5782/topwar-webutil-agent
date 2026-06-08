package com.hacademy.twagent.vo;

public record AnalyzeRequest(
	int server,
	String json,
	String lang
) {

}
