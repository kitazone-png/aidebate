# AI Debate Simulator - Setup and Deployment Guide

## Quick Start

This guide will help you set up and run the AI Debate Simulator on your local machine.

## Prerequisites

- **JDK 21** - Download from [Oracle](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/)
- **Maven 3.8+** - Download from [Apache Maven](https://maven.apache.org/download.cgi)
- **MySQL 8.0+** - Download from [MySQL](https://dev.mysql.com/downloads/mysql/)
- **Git** - For cloning the repository

## Step-by-Step Setup

### 1. Database Setup

#### 1.1 Install MySQL (if not already installed)
```bash
# On Ubuntu/Debian
sudo apt-get update
sudo apt-get install mysql-server

# Start MySQL service
sudo systemctl start mysql
sudo systemctl enable mysql
```

#### 1.2 Create Database and Tables
```bash
# Login to MySQL
mysql -u root -p

# Create database
CREATE DATABASE IF NOT EXISTS aidebate DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# Exit MySQL
exit;

# Import schema
mysql -u root -p aidebate < aidebate-start/src/main/resources/db/schema.sql

# Import initial data
mysql -u root -p aidebate < aidebate-start/src/main/resources/db/data.sql
```

### 2. Configure Application

#### 2.1 Update Database Credentials
Edit `aidebate-start/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aidebate?...
    username: root  # Your MySQL username
    password: your_password  # Your MySQL password
```

#### 2.2 Configure AI Service (Optional)
If you have an Alibaba Cloud AI API key:

```bash
export ALIBABA_AI_API_KEY=your_api_key_here
```

Or update in `application.yml`:
```yaml
spring:
  ai:
    alibaba:
      api-key: your_api_key_here
```

### 3. Build the Project

```bash
# Navigate to project root
cd /data/workspace/aidebate

# Clean and build
mvn clean install

# This will:
# - Compile all modules
# - Run tests
# - Package the application
```

### 4. Run the Application

```bash
# Method 1: Using Maven
cd aidebate-start
mvn spring-boot:run

# Method 2: Using JAR file
cd aidebate-start/target
java -jar aidebate-start-1.0.0-SNAPSHOT.jar
```

### 5. Access the Application

Once the application starts successfully:

- **Web Application**: http://localhost:8080
- **Health Check**: http://localhost:8080/api/health
- **API Topics**: http://localhost:8080/api/topics

## Default Credentials

### Admin Panel
- Username: `admin`
- Password: `admin`

### Test User
- Username: `testuser`
- Password: `password123`

## Verify Installation

### Test 1: Health Check
```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{
  "status": "UP",
  "application": "AI Debate Simulator",
  "version": "1.0.0-SNAPSHOT"
}
```

### Test 2: Database Connection
```bash
curl http://localhost:8080/api/topics
```

Should return a list of debate topics from the database.

## Project Structure

```
aidebate/
├── aidebate-adapter/         # Web controllers, REST APIs
├── aidebate-app/            # Application services
├── aidebate-domain/         # Domain models and business logic
├── aidebate-infrastructure/ # Database access, external APIs
├── aidebate-start/          # Main application and configuration
│   ├── src/main/resources/
│   │   ├── db/
│   │   │   ├── schema.sql   # Database schema
│   │   │   └── data.sql     # Initial data
│   │   ├── static/
│   │   │   └── index.html   # Frontend UI
│   │   └── application.yml  # Configuration
│   └── pom.xml
├── pom.xml                  # Parent POM
└── README.md
```

## Troubleshooting

### Issue: "Connection refused" to MySQL
**Solution**: Make sure MySQL is running
```bash
sudo systemctl status mysql
sudo systemctl start mysql
```

### Issue: "Access denied for user"
**Solution**: Check MySQL credentials in `application.yml`

### Issue: "Table doesn't exist"
**Solution**: Re-run the schema.sql script
```bash
mysql -u root -p aidebate < aidebate-start/src/main/resources/db/schema.sql
```

### Issue: "Port 8080 already in use"
**Solution**: Change port in `application.yml`:
```yaml
server:
  port: 8081  # Use a different port
```

### Issue: Compilation errors
**Solution**: Ensure JDK 21 is being used
```bash
java -version  # Should show version 21
mvn -version   # Check Maven is using JDK 21
```

## Development Mode

For development with hot reload:

```bash
# Terminal 1: Run backend
cd aidebate-start
mvn spring-boot:run

# Terminal 2: Frontend is served directly from static folder
# Just refresh your browser to see changes
```

## Environment Variables

The application supports these environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `MYSQL_PASSWORD` | root | MySQL database password |
| `ALIBABA_AI_API_KEY` | - | Alibaba Cloud AI API key |
| `ALIBABA_AI_MODEL` | qwen-max | AI model to use |
| `REDIS_HOST` | localhost | Redis host (optional) |
| `REDIS_PORT` | 6379 | Redis port (optional) |
| `REDIS_PASSWORD` | - | Redis password (optional) |

## Next Steps

1. **Explore the UI**: Open http://localhost:8080 in your browser
2. **Test Debate Topics**: Click "View Topics" to see available debate topics
3. **Admin Panel**: Access admin features (future implementation)
4. **API Documentation**: Explore available endpoints

## Features Implemented

✅ COLA 4 Architecture
✅ Database Schema (11 tables)
✅ Domain Models
✅ MyBatis Plus Mappers
✅ Spring Boot Configuration
✅ Content Moderation Service
✅ REST Controllers
✅ Wasteland-themed Frontend UI
✅ Health Check Endpoint
✅ Topic Listing API

## Features Pending (Future Development)

- Complete AI Service Integration with Spring AI Alibaba
- Full Debate Flow Implementation
- WebSocket for Real-time Debates
- Scoring System with AI Judges
- Admin Dashboard
- User Authentication
- Complete Unit Tests

## Support

For issues or questions:
- Check the logs in `logs/aidebate.log`
- Review error messages in the console
- Verify database connectivity
- Ensure all environment variables are set correctly

## License

Proprietary - All Rights Reserved
