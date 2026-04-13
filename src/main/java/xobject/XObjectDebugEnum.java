package xobject;

import java.util.TreeSet;

public class XObjectDebugEnum
{
	static TreeSet<String> foundValues = new TreeSet<String>();
	
	static public boolean checkString(String s)
	{
		if (!foundValues.contains(s))
		{
			System.out.println("\"" + s + "\",");
			foundValues.add(s);
		}
		
		return true;
	}
}
