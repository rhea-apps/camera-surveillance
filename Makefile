run: 
	@./gradlew run
install:
	@./gradlew installDist
update:
	@./gradlew installDist --refresh-dependencies
idea: 
	@./gradlew idea
clean: 
	@./gradlew clean 
