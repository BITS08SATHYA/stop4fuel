#!/bin/bash

# Function to kill background processes on exit
cleanup() {
    echo "Stopping services..."
    kill $(jobs -p)
    exit
}

trap cleanup SIGINT SIGTERM

# Dev mode: auth disabled, no Cognito dependency
# For production auth testing, use: AUTH_ENABLED=true ./start_services.sh
export AUTH_ENABLED=${AUTH_ENABLED:-false}
export DATABASE_URL=${DATABASE_URL:-jdbc:postgresql://localhost:5432/stopforfuel}
export DATABASE_PASSWORD=${DATABASE_PASSWORD:-password}

echo "Starting Spring Boot Backend on port 8080 (AUTH_ENABLED=$AUTH_ENABLED)..."
cd backend
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun &
BACKEND_PID=$!
cd ..

echo "Backend started. Press Ctrl+C to stop."
wait $BACKEND_PID
