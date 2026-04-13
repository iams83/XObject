package xobject;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class IdentifiedObjects
{
	final private ArrayList<String> duplicated = new ArrayList<String>();
	
	final private TreeMap<String,Object> objects = new TreeMap<String,Object>();

	public boolean containsKey(String key, Class<?> clazz)
	{
		return this.objects.containsKey(key);
	}

	public void put(String key, Object value)
	{
		this.objects.put(key, value);
	}

	public Object get(String key, Class<?> clazz)
	{
		return this.objects.get(key);
	}

	public Object addDuplicated(String key, Class<?> clazz)
	{
		return this.duplicated.add(key + "/" + clazz.getName());
	}
	
	public Set<Entry<String, Object>> entrySet()
	{
		return this.objects.entrySet();
	}

	public void clear()
	{
		this.duplicated.clear();
		
		this.objects.clear();
	}

	public String getKey(Object o)
	{
		for (Map.Entry<String,Object> entry : this.objects.entrySet())
		{
			if (entry.getValue() == o)
				return entry.getKey();
		}
		
		return null;
	}
}
