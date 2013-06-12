import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/** Use alldifferent.tsv created by FromLemon and add correlated resource labels to it. Because only a small amount of them are properties (which already exist),
 * we also need to get correlated classes, where we need to find out how (because just select ?c {?s a ?c. ?s a &lt;x&gt;.} might be too simple.**/
public class LemonDifferentCorrelated
{

	public static void main(String[] args) throws FileNotFoundException
	{
		try(Scanner in = new Scanner(new File("lemon/alldifferent.tsv")))
		{
			while(in.hasNextLine())
			{
				String line = in.nextLine();
				if(line.isEmpty()) {continue;}
				System.out.println(line);
				String[] tokens = line.split("\t");
				String resource = tokens[1];
				String resourceName = resource.substring(resource.lastIndexOf('/')+1);
				// it's a property
				if(Character.isLowerCase(resourceName.charAt(0)))
				{

				}
				// it's a class
				else if(Character.isUpperCase(resourceName.charAt(0)))
				{

				}
				else {throw new RuntimeException(resourceName);}
			}
		}
	}

}
