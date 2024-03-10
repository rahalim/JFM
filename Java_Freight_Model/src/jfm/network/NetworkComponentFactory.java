package jfm.network;

import java.lang.reflect.Constructor;
import java.util.HashMap;


/**
 * @author jhkwakkel
 *	Factory for producing nodes and edges for the physical network
 *
 */
public class NetworkComponentFactory {
	private HashMap<String, Class<?>> register = new HashMap<String, Class<?>>();
	protected static NetworkComponentFactory instance; //the singleton Factory instance
	
	private NetworkComponentFactory(){
		// factory should be a singleton. The default way to implement
		// a singleton pattern in Java is via a private constructor
		// and a static getFactory method
	}
	
	
	public static NetworkComponentFactory getFactory(){
		if (instance==null){
			instance = new NetworkComponentFactory();
		}
		return instance;
	}
	
	public void registerProduct (String productID, Class<?> productClass)
	{
		register.put(productID, productClass);
	}

	/**
	 * 
	 * @param productID ID of the desired product
	 * @param paramTypes classes of the constructor arguments for the desired product
	 * @param params the actual parameters for the constructor
	 * @return
	 */
    public Object createProduct(String productID, Class<?>[] paramTypes,
            				  Object[] params) {
        Object obj = null;

        try {
            Class<?> cls = register.get(productID);

            if (cls == null) {
                throw new RuntimeException("No class registered under " +
                		productID);
            }

            Constructor<?> ctor = cls.getConstructor(paramTypes);
            obj = ctor.newInstance(params);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return obj;
    }

}