package ma.shaur.bettercoppergolem.client.gui;

import java.util.Objects;
import java.util.function.Predicate;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class FilteredEditBox extends EditBox
{
	private Predicate<String> filter = Objects::nonNull;

	public FilteredEditBox(Font font, int width, int height, Component narration)
	{
		super(font, width, height, narration);
	}

	public void setFilter(Predicate<String> filter)
	{
		this.filter = filter;
	}
	
	@Override
	public void setValue(String value)
	{
		if(filter.test(value)) super.setValue(value);
	}
	
	@Override
	public void insertText(String input)
	{
		//I blame microslop
		String oldValue = getValue();
		super.insertText(input);
		if(!filter.test(getValue())) super.setValue(oldValue);
	}
	
	@Override
	public void deleteCharsToPos(int pos)
	{
		//Am I washed?
		String oldValue = getValue();
		super.deleteCharsToPos(pos);
		if(!filter.test(getValue())) super.setValue(oldValue);
	}
}
