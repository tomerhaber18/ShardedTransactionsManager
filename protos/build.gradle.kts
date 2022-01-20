
plugins {
    `java-library`
    idea
}

java {
    sourceSets.getByName("main").resources.srcDir("src/main/proto")
}
