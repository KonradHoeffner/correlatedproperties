import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

/**Input: two input files (output from LemonDifferentCorrelated) where manually some correlated labels were removed. Order needs to be the same.<br/>
 * Output: file of the same format only containing the differing lines and only those labels which were not the same**/
public class Diff
{
	static void diff(File f1, File f2) throws FileNotFoundException
	{				
		try(Scanner in1 = new Scanner(f1);Scanner in2 = new Scanner(f2))
		{
			Scanner[] in = {in1,in2};
			String[] lines = new String[2];	
			String[] keys = new String[2];
			String[][] tokens = new String[2][];
			boolean continuedSecond = false;
			while((in1.hasNextLine()||continuedSecond)&&in2.hasNextLine())
			{
				outer:
				for(int i=0;i<2;i++)
				{
					// only skip the empty line and not both
					if(!continuedSecond) {lines[i]=in[i].nextLine();} else {continuedSecond=false;}
					if(lines[i].isEmpty()) {if(i==1){continuedSecond=true;}continue outer;}
					tokens[i]=lines[i].trim().split("\\s*\t\\s*");
					keys[i]=Arrays.toString(Arrays.copyOf(tokens[i],2)); // key consists of keyword and resource
				}
				// now we are sure to have two nonempty lines and their tokens, we assume the right format (exception handling for different keys but not for less than two columns)
				if(!keys[0].equals(keys[1])) {throw new AssertionError("keys don't match: "+keys[0]+" vs "+keys[1]);}
				Set[] labelss = new Set[2];
				for(int i=0;i<2;i++)
				{
					labelss[i] = new TreeSet<>();
					for(int pos=2;pos<tokens[i].length;pos++)
					{
						labelss[i].add(tokens[i][pos]);//include numbers for now because then it's easier to get them in the output, if not add .split("=")[0]
					}
				}
				Set<String> union			= new TreeSet<>(labelss[0]);
				union.addAll(labelss[1]);
				Set<String> intersection	= new TreeSet<>(labelss[0]);
				intersection.retainAll(labelss[1]);
				Set<String> diff			= new TreeSet<>(union);diff.removeAll(intersection);
				if(!diff.isEmpty())
				{										
					System.out.println(CorrelatedProperties.tsv(keys[0],Arrays.toString(diff.toArray())));
				}
			}
		}
	}

	public static void main(String[] args) throws FileNotFoundException
	{
		diff(new File("input/test/diff1.tsv"),new File("input/test/diff2.tsv"));
	}

}
