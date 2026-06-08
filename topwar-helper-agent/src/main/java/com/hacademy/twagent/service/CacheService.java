package com.hacademy.twagent.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.hacademy.twagent.vo.CacheKey;

@Service
public class CacheService {
	
	private Map<CacheKey, String> cache = new ConcurrentHashMap<>();
	
	public String get(CacheKey key) {
		return cache.get(key);
	}
	
	public int size() {
		return cache.size();
	}
	
	public String push(CacheKey key, String json) {
		return cache.put(key, json); 
	}
	
}
