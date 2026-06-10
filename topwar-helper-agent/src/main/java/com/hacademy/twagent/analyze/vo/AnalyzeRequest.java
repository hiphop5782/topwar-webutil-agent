package com.hacademy.twagent.analyze.vo;

public record AnalyzeRequest(
	int server,
	String json,
	String lang,
	String time
) {

}
