# AI Debate Simulator

An AI-powered debate simulation platform built with COLA 4 architecture, Spring Boot 3.x, Vue.js, and Alibaba Cloud LLM.

## Project Structure (COLA 4)

```
aidebate/
├── aidebate-adapter/     # Adapter Layer (Controllers, APIs, WebSocket)
├── aidebate-app/         # Application Layer (Service Orchestration)
├── aidebate-domain/      # Domain Layer (Business Logic, Domain Models)
├── aidebate-infrastructure/  # Infrastructure Layer (DB Access, External APIs)
└── aidebate-start/       # Startup Module (Main Application, Configuration)
```

## Technology Stack

- **Backend**: Java 21, Spring Boot 3.2.0, Maven
- **Frontend**: Vue.js 3.x, Tailwind CSS
- **Database**: MySQL 8.0+, MyBatis Plus 3.5.5
- **Cache**: Redis 6.0+
- **AI Integration**: Spring AI Alibaba, Alibaba Cloud LLM
- **Architecture**: COLA 4 (Clean Object-oriented and Layered Architecture)

## Prerequisites

- JDK 21
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+ (optional)
- Node.js 18+ and npm (for frontend)

## Database Setup

1. **Create Database**:
   ```bash
   mysql -u root -p < aidebate-start/src/main/resources/db/schema.sql
   ```

2. **Initialize Data**:
   ```bash
   mysql -u root -p < aidebate-start/src/main/resources/db/data.sql
   ```

   Default credentials:
   - Admin: username=`admin`, password=`admin`
   - Test User: username=`testuser`, password=`password123`

## Configuration

1. Copy `application.yml.example` to `application.yml` in `aidebate-start/src/main/resources/`

2. Configure database connection:
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/aidebate
       username: your_mysql_username
       password: your_mysql_password
   ```

3. Configure Alibaba Cloud AI API key:
   ```yaml
   spring:
     ai:
       alibaba:
         api-key: ${ALIBABA_AI_API_KEY}
         model: qwen-max
   ```

   Set environment variable:
   ```bash
   export ALIBABA_AI_API_KEY=your_api_key_here
   ```

## Build and Run

### Backend

```bash
# Build the project
mvn clean install

# Run the application
cd aidebate-start
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### Frontend

```bash
cd aidebate-start/src/main/resources/static
npm install
npm run dev
```

## Features

- ✅ Debate topic management (hot topics, custom, AI-generated)
- ✅ Role-based debate system (Organizer, Moderator, Judges, Debaters)
- ✅ AI opponent with configurable personality and skill level
- ✅ Real-time debate interaction via WebSocket
- ✅ Intelligent scoring system with 3 AI judges
- ✅ Performance feedback and improvement suggestions
- ✅ Admin backend for debate management and analytics
- ✅ Sensitive word dictionary and content moderation
- ✅ Wasteland-themed UI with split-screen debate interface

## API Endpoints

### Public APIs
- `GET /api/topics` - List active debate topics
- `POST /api/debates` - Initialize a new debate session
- `POST /api/debates/{id}/arguments` - Submit an argument
- `GET /api/debates/{id}/scores` - Get current scores

### Admin APIs
- `POST /api/admin/login` - Admin login
- `GET /api/admin/debates` - List all debates
- `GET /api/admin/statistics` - Platform statistics
- `POST /api/admin/sensitive-words` - Manage sensitive words
- `POST /api/admin/moderation/toggle` - Toggle content validation

### WebSocket
- `/ws/debate/{sessionId}` - Real-time debate communication

## Testing

```bash
# Run all tests
mvn test

# Run specific module tests
cd aidebate-domain
mvn test
```

## Development Standards

- Follows Alibaba Java Development Manual (Huangshan Edition)
- Unit test coverage target: >80% for domain layer
- Code formatted with Google Java Style Guide
- Commits follow Conventional Commits specification

## Database Schema

The project includes 11 tables:
- `user` - User accounts
- `admin_user` - Admin accounts  
- `debate_topic` - Debate topics
- `debate_session` - Debate sessions
- `role` - Debate roles
- `argument` - Arguments
- `scoring_rule` - Scoring criteria
- `score_record` - Judge scores
- `feedback` - User feedback
- `sensitive_word` - Content moderation dictionary
- `system_configuration` - System settings

See `aidebate-start/src/main/resources/db/schema.sql` for complete DDL.

## License

Proprietary - All rights reserved

## Support

For issues and questions, please contact the development team.
