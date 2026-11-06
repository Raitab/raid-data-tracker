package com.raidtracker;

import java.util.List;
import lombok.Data;

/**
 * One unique on the raid line. Theatre and Tombs still use the older receiver fields.
 */
@Data
public class UniqueDrop {
	private String item = "";
	private int id;
	private boolean own;
	private String receiver = "";
	private Long value;

	/** This player received this item. Another unique on the same raid does not count. */
	public static boolean owns(List<UniqueDrop> drops, String itemName) {
		if (drops == null || itemName == null) {
			return false;
		}
		for (UniqueDrop drop : drops) {
			if (drop != null && drop.isOwn() && itemName.equalsIgnoreCase(drop.getItem())) {
				return true;
			}
		}
		return false;
	}
}
