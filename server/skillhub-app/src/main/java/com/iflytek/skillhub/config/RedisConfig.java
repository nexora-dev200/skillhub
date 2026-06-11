package com.iflytek.skillhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * Redis configuration supporting both standalone and cluster modes.
 * Mode selection is controlled via spring.data.redis.mode property:
 * - standalone (default): Single Redis instance
 * - cluster: Redis Cluster with multiple nodes
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.cluster.nodes:}")
    private List<String> clusterNodes;

    @Value("${spring.data.redis.cluster.max-redirects:3}")
    private int maxRedirects;

    /**
     * Creates Redis connection factory for standalone mode.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.mode", havingValue = "standalone", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "redisConnectionFactory")
    public LettuceConnectionFactory redisStandaloneConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }
        config.setDatabase(database);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder().build();
        return new LettuceConnectionFactory(config, clientConfig);
    }

    /**
     * Creates Redis connection factory for cluster mode.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.mode", havingValue = "cluster")
    @ConditionalOnMissingBean(name = "redisConnectionFactory")
    public LettuceConnectionFactory redisClusterConnectionFactory() {
        if (clusterNodes == null || clusterNodes.isEmpty()) {
            throw new IllegalStateException("Redis cluster nodes must be configured when using cluster mode");
        }

        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration();
        
        clusterConfig.setClusterNodes(clusterNodes.stream()
                .map(node -> {
                    String[] parts = node.split(":");
                    if (parts.length == 2) {
                        return new org.springframework.data.redis.connection.RedisNode(
                                parts[0], Integer.parseInt(parts[1]));
                    } else {
                        throw new IllegalArgumentException("Invalid cluster node format: " + node);
                    }
                })
                .toList());
        
        if (password != null && !password.isEmpty()) {
            clusterConfig.setPassword(password);
        }
        
        clusterConfig.setMaxRedirects(maxRedirects);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder().build();
        return new LettuceConnectionFactory(clusterConfig, clientConfig);
    }

    /**
     * Creates RedisTemplate bean for both modes.
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        
        return template;
    }
}
