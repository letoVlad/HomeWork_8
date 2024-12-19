import cacheService.Cache;
import cacheService.CacheProxy;
import serviceTest.DataService;
import serviceTest.DataServiceImpl;

public class Main {
    public static void main(String[] args) {

        DataService dataService = CacheProxy.createProxy(new DataServiceImpl());

        System.out.println(dataService.calculateSum(3, 4));
        System.out.println(dataService.calculateSum(3, 4));
        System.out.println(dataService.factorial(4));
        System.out.println(dataService.factorial(4));



    }
}