.PHONY: run build test clean docker-run docker-stop

# Default target: build and run the application
run: build
	java -jar target/*.jar

build:
	./mvnw clean package -DskipTests

test:
	./mvnw test

clean:
	./mvnw clean
	rm -rf data/

# Docker targets
docker-run:
	docker-compose up --build -d

docker-stop:
	docker-compose down
