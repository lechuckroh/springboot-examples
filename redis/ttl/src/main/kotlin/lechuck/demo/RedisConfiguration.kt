package lechuck.demo

import lechuck.demo.session.Session
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisKeyExpiredEvent
import org.springframework.data.redis.core.RedisKeyValueAdapter
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.convert.KeyspaceConfiguration
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import org.springframework.stereotype.Component
import java.util.*
import java.lang.reflect.Method
import org.springframework.cache.interceptor.KeyGenerator
import org.springframework.util.StringUtils

const val REDIS_PREFIX = "demo"

@Configuration
@EnableRedisRepositories(
    keyspaceConfiguration = MyKeyspaceConfiguration::class,
    enableKeyspaceEvents = RedisKeyValueAdapter.EnableKeyspaceEvents.ON_STARTUP
)
class RedisConfiguration {

    @Bean
    fun getRedisTemplate(factory: RedisConnectionFactory): RedisTemplate<String, Session> {
        val redisTemplate: RedisTemplate<String, Session> = RedisTemplate()
        redisTemplate.connectionFactory = factory
        return redisTemplate
    }
}

class MyKeyspaceConfiguration : KeyspaceConfiguration() {
    override fun initialConfiguration(): Iterable<KeyspaceSettings> {
        val settings = KeyspaceSettings(Session::class.java, "$REDIS_PREFIX:session")
        settings.setTimeToLive(120)
        return listOf(settings)
    }
}

class CacheKeyGenerator : KeyGenerator {
    override fun generate(target: Any, method: Method, vararg params: Any?): Any {
      return "${target.javaClass.simpleName}_${method.name}_${StringUtils.arrayToDelimitedString(params, "_")}"
    }
  }
  

@Component
class SessionExpiredEventListener {
    @EventListener
    fun handleRedisKeyExpiredEvent(event: RedisKeyExpiredEvent<Session>) {
        val session = event.value as Session
        Objects.requireNonNull(session)
        println("session expired. id:${session.id}, username:${session.username}")
    }
}
