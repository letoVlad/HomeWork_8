package cacheService;

import java.io.*;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CacheProxy implements InvocationHandler {
    private final Object target;
    private Map<String, Object> cache = new HashMap<>();

    public CacheProxy(Object target) {
        this.target = target;
    }

    /**
     * Переопределяет метод invoke, который перехватывает вызовы методов целевого объекта.
     *
     * @param proxy  прокси-объект
     * @param method метод, который был вызван
     * @param args   аргументы метода
     * @return результат выполнения метода
     * @throws Throwable исключения, возникающие при выполнении метода
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if (method.isAnnotationPresent(Cache.class)) {
            Cache cacheAnnotation = method.getAnnotation(Cache.class);
            String key = generateCacheKey(method, args);

            if (cache.containsKey(key)) {// Ищем рез. в памяти
                return cache.get(key);
            }

            if (cacheAnnotation.cacheType() == CacheType.FILE) { // Ищем рез. в файле
                Object fileResult = loadFromCache(key);
                if (fileResult != null) {
                    return fileResult;
                }
            }

            Object result = method.invoke(target, args); //Если результат не найден, выполнить метод

            switch (cacheAnnotation.cacheType()) { // Сохранить результат в кэш
                case IN_MEMORY -> saveToMemory(method, this.target, args);
                case FILE -> saveToFile(method, args, key);
            }
            return result;
        }
        return method.invoke(target, args);
    }

    /**
     * Загружает результат из кэша в файл.
     *
     * @param methodName имя метода для поиска в кэше
     * @return восстановленный результат из файла
     */
    private Object loadFromCache(String methodName) {
        File cacheDir = new File("cache");
        File file = new File(cacheDir, methodName + ".txt");

        if (!file.exists()) {
            return null;
        }

        try (FileInputStream fileInputStream = new FileInputStream(file);
             ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            System.out.println("Объект восстановлен из файла: " + file.getAbsolutePath());
            return objectInputStream.readObject();

        } catch (IOException e) {
            throw new RuntimeException("Не удалось восстановить объект из файла: " + file.getAbsolutePath(), e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Сохраняет результат выполнения метода в память.
     */
    private void saveToMemory(Method method, Object target, Object[] args) throws InvocationTargetException,
            IllegalAccessException {

        String key = generateCacheKey(method, args);
        Object result = method.invoke(target, args);
        cache.put(key, result);
    }

    /**
     * Сохраняет результат выполнения метода в файл.
     *
     * @param method метод, который был вызван
     * @param args   аргументы метода
     * @param key    ключ кэша для файла
     */
    private void saveToFile(Method method, Object[] args, String key) throws InvocationTargetException, IllegalAccessException {
        String methodName = method.getName();
        File outputFileName = getFile(method, key, methodName);
        Object result = method.invoke(target, args);

        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFileName);
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
            objectOutputStream.writeObject(result);
            System.out.println("Файл сохранен: " + outputFileName.getAbsolutePath());
            cache.put(key, result);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось записать файл: " + outputFileName.getAbsolutePath(), e);
        }

        if (method.isAnnotationPresent(Cache.class)) {
            Cache cacheAnnotation = method.getAnnotation(Cache.class);
            if (cacheAnnotation.archiveToZIP()) {
                archiveToZip(outputFileName);
            }
        }
    }

    /**
     * Создает файл для сохранения результата кэширования.
     *
     * @param method       метод, для которого создается файл
     * @param argsHashCode хеш-значение аргументов метода
     * @param methodName   имя метода
     * @return файл для кэширования
     */
    private static File getFile(Method method, String argsHashCode, String methodName) {
        File cacheDir = new File("cache");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new RuntimeException("Не удалось создать каталог кэша.");
        }

        String fileNamePrefix = method.isAnnotationPresent(Cache.class)
                ? method.getAnnotation(Cache.class).fileNamePrefix()
                : "";

        String fileName = (fileNamePrefix.isEmpty() ? argsHashCode : fileNamePrefix) + ".txt";

        return new File(cacheDir, fileName);
    }

    /**
     * Архивирует файл в формат ZIP.
     *
     * @param file файл, который необходимо архивировать
     */
    private void archiveToZip(File file) {
        String zipFilePath = file.getAbsolutePath() + ".zip";

        try (FileOutputStream fileOutputStream = new FileOutputStream(zipFilePath);
             ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
             FileInputStream fileInputStream = new FileInputStream(file)) {

            zipOutputStream.putNextEntry(new ZipEntry(file.getName()));
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fileInputStream.read(buffer)) >= 0) {
                zipOutputStream.write(buffer, 0, length);
            }
            zipOutputStream.closeEntry();
            System.out.println("Файл заархивирован: " + zipFilePath);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось заархивировать файл: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Генерирует ключ для кэширования на основе имени метода и его аргументов.
     *
     * @param method метод, для которого генерируется ключ
     * @param args   аргументы метода
     * @return ключ кэша
     */
    private String generateCacheKey(Method method, Object[] args) {
        StringBuilder key = new StringBuilder(method.getName());
        Cache cacheAnnotation = method.getAnnotation(Cache.class);
        Class<?>[] excludedArgTypes = cacheAnnotation.includedArgTypes();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < args.length; i++) {
            boolean shouldIgnore = false;

            for (Class<?> excludedType : excludedArgTypes) {
                if (parameters[i].getType().equals(excludedType)) {
                    shouldIgnore = true;
                    break;
                }
            }
            if (shouldIgnore) {
                continue;
            }
            key.append("_").append(args[i]);
        }
        return key.toString();
    }

    /**
     * Создает прокси-объект для целевого объекта с кэшированием.
     * @param target целевой объект
     * @return прокси-объект с функциональностью кэширования
     */
    public static <T> T createProxy(T target) {
        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new CacheProxy(target)
        );
    }
}
