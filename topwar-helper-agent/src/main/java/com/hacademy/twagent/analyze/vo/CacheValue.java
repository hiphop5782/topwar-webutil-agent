package com.hacademy.twagent.analyze.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CacheValue {
	private String time;
	private String cachedJson;
}
