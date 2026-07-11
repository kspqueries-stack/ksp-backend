package com.policescheduler.service;

import com.policescheduler.entity.SystemConfig;
import com.policescheduler.repository.SystemConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminConfigService {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigService.class);

    private final SystemConfigRepository systemConfigRepository;

    public AdminConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    public List<SystemConfig> getConfig() {
        return systemConfigRepository.findAll();
    }

    @Transactional
    public List<SystemConfig> updateConfig(Map<String, String> configUpdates) {
        configUpdates.forEach((key, value) -> {
            SystemConfig config = systemConfigRepository.findByConfigKey(key)
                    .orElseGet(() -> {
                        SystemConfig newConfig = new SystemConfig();
                        newConfig.setConfigKey(key);
                        return newConfig;
                    });
            config.setConfigValue(value);
            systemConfigRepository.save(config);
            log.info("Updated system config: {} = {}", key, value);
        });
        return systemConfigRepository.findAll();
    }
}
