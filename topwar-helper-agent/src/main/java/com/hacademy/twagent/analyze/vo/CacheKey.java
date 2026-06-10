package com.hacademy.twagent.analyze.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(of = {"server", "lang"})
public class CacheKey {
	int server;
	String lang;
}
