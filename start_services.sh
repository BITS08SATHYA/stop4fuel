#!/bin/bash

# Function to kill background processes on exit
cleanup() {
    echo "Stopping services..."
    kill $(jobs -p)
    exit
}

trap cleanup SIGINT SIGTERM

echo "Starting Spring Boot Backend on port 8080..."
cd backend
./gradlew bootRun &
BACKEND_PID=$!
cd ..

echo "Backend started. Press Ctrl+C to stop."
wait $BACKEND_PID
