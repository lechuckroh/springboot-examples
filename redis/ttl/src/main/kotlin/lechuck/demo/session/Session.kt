package lechuck.demo.session

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import java.io.Serializable

@RedisHash(timeToLive = 60)
class Session(
        @Id var id: String,
        var username: String,
) : Serializable
