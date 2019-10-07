package com.alicp.jetcache.autoconfigure;

import com.alicp.jetcache.CacheBuilder;
import com.alicp.jetcache.CacheConfigException;
import com.alicp.jetcache.external.ExternalCacheBuilder;
import com.alicp.jetcache.redis.lettuce.JetCacheCodec;
import com.alicp.jetcache.redis.lettuce.LettuceConnectionManager;
import com.alicp.jetcache.redis.lettuce.RedisLettuceCacheBuilder;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.masterslave.MasterSlave;
import io.lettuce.core.masterslave.StatefulRedisMasterSlaveConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created on 2017/5/10.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
@Configuration
@Conditional(RedisLettuceAutoConfiguration.RedisLettuceCondition.class)
public class RedisLettuceAutoConfiguration {
    public static final String AUTO_INIT_BEAN_NAME = "redisLettuceAutoInit";

    public static class RedisLettuceCondition extends JetCacheCondition {
        public RedisLettuceCondition() {
            super("redis.lettuce");
        }
    }

    @Bean(name = {AUTO_INIT_BEAN_NAME})
    public RedisLettuceAutoInit redisLettuceAutoInit() {
        return new RedisLettuceAutoInit();
    }

    public static class RedisLettuceAutoInit extends ExternalCacheAutoInit {

        public RedisLettuceAutoInit() {
            super("redis.lettuce");
        }

        @Override
        protected CacheBuilder initCache(ConfigTree ct, String cacheAreaWithPrefix) {
            Map<String, Object> map = ct.subTree("uri"/*there is no dot*/).getProperties();
            String readFromStr = ct.getProperty("readFrom");
            ReadFrom readFrom = null;
            if (readFromStr != null) {
                readFrom = ReadFrom.valueOf(readFromStr.trim());
            }

            AbstractRedisClient client;
            StatefulConnection connection = null;
            if (map == null || map.size() == 0) {
                throw new CacheConfigException("uri is required");
            } else if (map.size() == 1) {
                String uri = (String) map.values().iterator().next();
                if (readFrom == null) {
                    client = RedisClient.create(uri);
                } else {
                    client = RedisClient.create();
                    StatefulRedisMasterSlaveConnection c = MasterSlave.connect(
                            (RedisClient) client,
                            new JetCacheCodec(),
                            RedisURI.create(uri));
                    c.setReadFrom(readFrom);
                    connection = c;
                }
            } else {
                List<RedisURI> list = map.values().stream().map((k) -> RedisURI.create(URI.create(k.toString())))
                        .collect(Collectors.toList());
                client = RedisClusterClient.create(list);
                if (readFrom != null) {
                    StatefulRedisClusterConnection c = ((RedisClusterClient) client).connect(new JetCacheCodec());
                    c.setReadFrom(readFrom);
                    connection = c;
                }
            }

            ExternalCacheBuilder externalCacheBuilder = RedisLettuceCacheBuilder.createRedisLettuceCacheBuilder()
                    .connection(connection)
                    .redisClient(client);
            parseGeneralConfig(externalCacheBuilder, ct);

            // eg: "remote.default.client"
            autoConfigureBeans.getCustomContainer().put(cacheAreaWithPrefix + ".client", client);
            LettuceConnectionManager m = LettuceConnectionManager.defaultManager();
            m.init(client, connection);
            autoConfigureBeans.getCustomContainer().put(cacheAreaWithPrefix + ".connection", m.connection(client));
            autoConfigureBeans.getCustomContainer().put(cacheAreaWithPrefix + ".commands", m.commands(client));
            autoConfigureBeans.getCustomContainer().put(cacheAreaWithPrefix + ".asyncCommands", m.asyncCommands(client));
            autoConfigureBeans.getCustomContainer().put(cacheAreaWithPrefix + ".reactiveCommands", m.reactiveCommands(client));
            return externalCacheBuilder;
        }
    }
}
