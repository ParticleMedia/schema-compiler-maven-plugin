build:
	@mvn clean install

test:
	@mvn clean test

release:
	@mvn clean deploy

include .makefiles/*.mk
