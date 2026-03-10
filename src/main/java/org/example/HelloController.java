package org.example;

@RestController
public class HelloController {

    @GetMapping("/")
    public static String index() {
        return "Greetings from Spring Boot!";
    }
    @GetMapping("/pi")
    public static String getPI() {
        return "PI = " + Math.PI;
    }
    @GetMapping("/hello")
    public static String helloW() {
        return "Greetings from Spring Boot!";
    }


}
