package com.hacademy.twagent.analyze.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.hacademy.twagent.analyze.vo.CacheKey;
import com.hacademy.twagent.analyze.vo.CacheValue;

@Service
public class CacheService {
	
	private Map<CacheKey, CacheValue> cache = new ConcurrentHashMap<>();
	
	public CacheValue get(CacheKey key) {
		return cache.get(key);
	}
	
	public int size() {
		return cache.size();
	}
	
	public CacheValue push(CacheKey key, CacheValue value) {
		return cache.put(key, value); 
	}
	
	public CacheValue remove(CacheKey key) {
		return cache.remove(key);
	}
	
}
