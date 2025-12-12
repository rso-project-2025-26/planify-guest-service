SERVICE_NAME=user-service
VERSION=0.0.1

build:
	mvn clean package -DskipTests

docker-build:
	docker build -t planify/$(SERVICE_NAME):$(VERSION) .

docker-run:
	docker run -p 8080:8080 planify/$(SERVICE_NAME):$(VERSION)

test:
	mvn test
