# Kafka 与 SpringBoot 继承，有两种方式：

## 第一种, springboot 根据application.properties 自动进行配置
	只需要在application.properties 中设置 producer， consumer的相关配置，由springboot自动完成相关Bean的装配。
	参考文档；https://docs.spring.io/spring-boot/docs/current/reference/html/common-application-properties.html
	
## 第二种，手动配置相关Bean
	在配置文件中声明kafka相关的属性，手动发布Kafak相关的Bean到spring容器中
	
	
	