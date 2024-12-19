package serviceTest;

public class DataServiceImpl implements DataService {

    @Override
    public int calculateSum(int a, int b) {
        return a + b;
    }

    @Override
    public int factorial(int number) {
        if (number <= 0) {
            throw new IllegalArgumentException("Вычисление факториала отрицательного числа невозможно!");
        }
        int factorial = 1;
        for (int i = 2; i <= number; i++) {
            factorial *= i;
        }

        return factorial;
    }
}
