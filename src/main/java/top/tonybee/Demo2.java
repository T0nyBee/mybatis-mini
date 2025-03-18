package top.tonybee;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class Demo2 {
    public static void main(String[] args) {
        Drink milkDrink = new MilkDrink();
        Drink colaDrink = new ColaDrink();
        System.out.println(milkDrink.drink());
        System.out.println(colaDrink.drink());

        System.out.println(DrinkFactory.getDrink("Milk").drink());
        System.out.println(DrinkFactory.getDrink("Cola").drink());
    }

    static interface Drink {
        String drink();
    }

    // 有两个实现类，它们工作一切正常
    static class MilkDrink implements Drink {
        public String drink() {
            return "Drink Milk!";
        }
    }

    static class ColaDrink implements Drink {
        public String drink() {
            return "Drink Cola!";
        }
    }

    // 新需求：喝饮料步骤要细化，喝前要打开包装，喝完要清理垃圾
    // 这个只是封装，实际上直接写在invoke()里也是一样的
    static class AroundDrinkAction {
        public String beforeDrink() {
            return "拆开包装";
        }

        public String afterDrink() {
            return "清理垃圾";
        }
    }

    // 更新代理之后，搞一个工厂来创建对象，屏蔽内部的代理细节
    static class DrinkFactory {
        private final static AroundDrinkAction AROUND_DRINK_ACTION = new AroundDrinkAction();
        private DrinkFactory() {}

        public static Drink getDrink(String drink) {
            AroundDrinkInvocationHandler around;
            switch (drink) {
                case "Milk":
                    around = new AroundDrinkInvocationHandler(new MilkDrink(), AROUND_DRINK_ACTION);
                    break;
                case "Cola":
                    around = new AroundDrinkInvocationHandler(new ColaDrink(), AROUND_DRINK_ACTION);
                    break;
                default:
                    around = null;
                    break;
            }
            return (Drink) Proxy.newProxyInstance(Demo2.class.getClassLoader(), new Class[]{Drink.class}, around);
        }
    }

    static class AroundDrinkInvocationHandler implements InvocationHandler {
        private final Drink drink;
        private final AroundDrinkAction aroundDrinkAction;

        public AroundDrinkInvocationHandler(Drink drink, AroundDrinkAction aroundDrinkAction) {
            this.drink = drink;
            this.aroundDrinkAction = aroundDrinkAction;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            StringBuilder sb = new StringBuilder();
            sb.append(aroundDrinkAction.beforeDrink());
            sb.append("\n");
            sb.append(method.invoke(drink, args));
            sb.append("\n");
            sb.append(aroundDrinkAction.afterDrink());
            sb.append("\n");
            return sb.toString();
        }
    }

}
