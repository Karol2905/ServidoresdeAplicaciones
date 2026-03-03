package org.example;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class MicroSpringBoot {
    public static Map<String,Method> controllerMethods = new HashMap<>();
    public static void main(String[] args) throws ClassNotFoundException, InvocationTargetException, IllegalAccessException {
        System.out.println("Loading rest controllers and their methods... ");

        Class c = Class.forName(args[0]);

        if (c.isAnnotationPresent(RestController.class)){
            for (Method m:c.getDeclaredMethods()){
                if (m.isAnnotationPresent(GetMapping.class)){
                    GetMapping a= m.getAnnotation(GetMapping.class);
                    String path = a.value();
                    controllerMethods.put(path,m);
                }
            }
        }

        System.out.println("Running method controller for  path: "+args[1]);

        Method m = controllerMethods.get(args[1]);

        System.out.println(m.invoke(null));
    }
}
