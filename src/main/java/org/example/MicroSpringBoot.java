package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

public class MicroSpringBoot {


    public static Map<String, Method> controllerMethods = new HashMap<>();
    public static Map<String, Object> controllerInstances = new HashMap<>();

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        System.out.println("Loading rest controllers and their methods... ");


        if (args.length > 0) {
            loadController(args[0]);
        }


        List<Class<?>> found = scanClasspath("org.example");
        for (Class<?> c : found) {
            loadController(c.getName());
        }

        System.out.println("\nRegistered routes:");
        controllerMethods.forEach((path, method) ->
                System.out.printf("  GET %-20s -> %s.%s%n",
                        path,
                        method.getDeclaringClass().getSimpleName(),
                        method.getName()));


        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", MicroSpringBoot::handleRequest);
        server.setExecutor(null); // single-threaded: no concurrente
        server.start();

        System.out.println("\nServer running at http://localhost:" + PORT + "/");
        System.out.println("Press Ctrl+C to stop.");
    }


    private static void loadController(String className) throws Exception {
        Class<?> c;
        try {
            c = Class.forName(className);
        } catch (ClassNotFoundException e) {
            System.err.println("Class not found: " + className);
            return;
        }

        if (!c.isAnnotationPresent(RestController.class)) return;


        Object instance = null;
        try {
            instance = c.getDeclaredConstructor().newInstance();
        } catch (Exception ignored) {

        }


        for (Method m : c.getDeclaredMethods()) {
            if (m.isAnnotationPresent(GetMapping.class)) {
                GetMapping a = m.getAnnotation(GetMapping.class);
                String path = a.value();
                if (!controllerMethods.containsKey(path)) {
                    controllerMethods.put(path, m);
                    controllerInstances.put(path, instance);
                }
            }
        }
    }


    private static List<Class<?>> scanClasspath(String packageName) {
        List<Class<?>> result = new ArrayList<>();
        String packagePath = packageName.replace('.', '/');
        try {
            Enumeration<java.net.URL> resources =
                    Thread.currentThread().getContextClassLoader().getResources(packagePath);
            while (resources.hasMoreElements()) {
                java.net.URL url = resources.nextElement();
                File dir = new File(url.toURI());
                if (dir.isDirectory()) {
                    for (File f : Objects.requireNonNull(dir.listFiles())) {
                        if (f.getName().endsWith(".class")) {
                            String name = packageName + "." + f.getName().replace(".class", "");
                            try {
                                Class<?> cls = Class.forName(name);
                                if (cls.isAnnotationPresent(RestController.class)) {
                                    result.add(cls);
                                    System.out.println("  Found @RestController: " + name);
                                }
                            } catch (ClassNotFoundException | NoClassDefFoundError ignored) { }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Scan error: " + e.getMessage());
        }
        return result;
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        String query = uri.getRawQuery();

        // 1. Ruta registrada por un @RestController
        if (controllerMethods.containsKey(path)) {
            try {
                Method m = controllerMethods.get(path);
                Object bean = controllerInstances.get(path);
                Object result = invokeWithParams(m, bean, parseQuery(query));
                sendText(exchange, 200, result != null ? result.toString() : "");
            } catch (Exception e) {
                sendText(exchange, 500, "Error: " + e.getMessage());
            }
            return;
        }


        if (serveStaticFile(path, exchange)) return;


        sendHtml(exchange, 404,
                "<html><body><h1>404 - Not Found</h1><p>" + path + "</p></body></html>");
    }

    private static Object invokeWithParams(Method m, Object instance,
                                           Map<String, String> queryParams) throws Exception {
        Parameter[] params = m.getParameters();
        Object[] callArgs = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(RequestParam.class)) {
                RequestParam rp = params[i].getAnnotation(RequestParam.class);
                String val = queryParams.getOrDefault(rp.value(),
                        rp.defaultValue().isEmpty() ? null : rp.defaultValue());
                callArgs[i] = val;
            }
        }
        return m.invoke(instance, callArgs);
    }


    private static boolean serveStaticFile(String path, HttpExchange exchange) throws IOException {
        if (path.contains("..")) { sendText(exchange, 403, "Forbidden"); return true; }

        InputStream is = MicroSpringBoot.class.getResourceAsStream("/static" + path);
        if (is == null) {
            Path fs = Paths.get("src/main/resources/static" + path);
            if (Files.exists(fs)) is = Files.newInputStream(fs);
        }
        if (is == null) return false;

        byte[] bytes = is.readAllBytes();
        is.close();
        exchange.getResponseHeaders().set("Content-Type", contentType(path));
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
        return true;
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".css"))  return "text/css";
        if (path.endsWith(".js"))   return "application/javascript";
        return "application/octet-stream";
    }


    private static void sendText(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static void sendHtml(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isBlank()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            try {
                map.put(java.net.URLDecoder.decode(kv[0], "UTF-8"),
                        kv.length > 1 ? java.net.URLDecoder.decode(kv[1], "UTF-8") : "");
            } catch (Exception ignored) { }
        }
        return map;
    }
}
