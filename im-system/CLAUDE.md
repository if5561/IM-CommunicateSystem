# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a multi-module Java-based Instant Messaging (IM) System built with Spring Boot 2.7.12 and Maven. The system uses微服务架构with separate modules for different concerns:

- **common**: Shared utilities, models, enums, and configuration
- **service**: REST API layer for user management, friendships, groups, and message handling
- **im-tcp**: Netty-based TCP/WebSocket gateway for real-time connections
- **im-codec**: Protocol buffer serialization and message encoding/decoding
- **im-message-store**: Message persistence service with RabbitMQ integration

## Development Commands

### Build and Package
```bash
mvn clean install          # Build all modules
mvn clean package          # Package all applications
mvn clean compile          # Compile source code
mvn test                   # Run all tests
mvn test -pl service       # Run tests for specific module
```

### Run Applications
```bash
# Service API (port 8000)
java -jar service/target/service-*.jar

# TCP Gateway (TCP: 9000, WebSocket: 19000)
java -jar im-tcp/target/im-tcp-*.jar

# Message Store Service
java -jar im-message-store/target/im-message-store-*.jar
```

### Database Setup
The system requires MySQL with database `im_core`. Connection details:
- URL: `jdbc:mysql://localhost:3306/im_core`
- Username: `root`
- Password: `123456`

Additional dependencies:
- Redis (default: localhost:6379)
- RabbitMQ (default: localhost:5672)
- ZooKeeper (for service discovery)

## Architecture Patterns

### Message Flow
1. Clients connect to im-tcp gateway via TCP/WebSocket
2. Gateway routes messages to service module via internal APIs
3. Service module handles business logic and database operations
4. Messages are queued via RabbitMQ to im-message-store for persistence
5. Online status and sessions managed in Redis

### Key Components
- **UserSessionUtils** (common): Manages user session data across modules
- **RouteInfo**: Handles user routing using consistent hashing (TreeMap-based)
- **CallbackService**: External HTTP callbacks for user/group operations
- **GroupChatManager**: Handles group creation, joining, messaging
- **ImServer**: Netty server with configurable boss/worker threads

### Protocol Details
- Uses Protocol Buffers (Protostuff) for message serialization
- WebSocket messages use custom codec (WebSocketMessageEncoder/Decoder)
- Message types defined in `MessageType` enum (private chat, group chat, etc.)
- Multi-device login supported with 4 modes: 1-allow 2-after-first-login 3-kick-other 4-reject

### Configuration Files
- Service: `service/src/main/resources/application.yml`
- TCP Gateway: `im-tcp/src/main/resources/config.yml`
- Logging: Uses logback with separate configurations per module

### Testing Approach
MyBatis Plus is used for database operations. Test SQL queries can be validated using MyBatis Plus wrapper conditions, e.g.:
```java
QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
queryWrapper.eq("user_id", userId);
```

## Important Notes
- The codebase uses Lombok for reducing boilerplate code
- FastJSON is used for JSON serialization
- Hutool utility library is extensively used
- Callback URLs are configured for external integrations (friend approval, group operations)
- Private key encryption is configured for secure operations