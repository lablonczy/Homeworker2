import java.text.SimpleDateFormat;

public class Assignment{

	private String name, due;

	public Assignment(String name, String due) {
		setName(name);
		setDue(due);
	}

	@Override
	public String toString() {
		return name + " | Due: " + due;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDue() {
		return due;
	}

	private String reduceDue(String due){
		if(due.charAt(due.length()-1) == 'T')
			return reduceDue(due.substring(0,due.lastIndexOf(" ")));
		else if(due.charAt((due.length() - 1))  == 'M')
			return reduceDue(due.substring(0,due.lastIndexOf(" ") ));
		else if(due.charAt(due.length()  - 3) == ':' && due.charAt(due.length() - 6) == ':')
			return reduceDue(due.substring(0,due.lastIndexOf(":")));
		else
			return due;
	}
	
	private String enforceFormat(String due) {
		int firstSpace = due.indexOf(" ");
		
		if((due.charAt(firstSpace) + 2) == ' ')
			return due.substring(0, firstSpace) + "0" + due.substring(firstSpace + 2);
		
		return due;
	}

	public void setDue(String due) {
		final String[] weekdays = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
		for(String day : weekdays)
			if(due.startsWith(day))
				due = due.substring(due.indexOf(",") + 2);

		due = reduceDue(due);
		
		due = enforceFormat(due);

		this.due = due;
	}

}

