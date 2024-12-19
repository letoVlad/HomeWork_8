package serviceTest;

import cacheService.Cache;
import cacheService.CacheType;

public interface DataService {

    @Cache(cacheType = CacheType.IN_MEMORY)
    int calculateSum(int a, int b);

    @Cache(cacheType = CacheType.FILE, archiveToZIP = true)
    int factorial(int number);
}
