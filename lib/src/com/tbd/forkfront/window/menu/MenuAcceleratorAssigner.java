package com.tbd.forkfront.window.menu;

import java.util.List;

public class MenuAcceleratorAssigner {
	public static void assign(List<MenuItem> items) {
		for (MenuItem i : items) {
			if (i.hasAcc()) {
				return;
			}
		}
		char acc = 'a';
		for (MenuItem i : items) {
			if (!i.isHeader() && i.isSelectable() && acc != 0) {
				i.setAcc(acc);
				acc++;
				if (acc == 'z' + 1) {
					acc = 'A';
				} else if (acc == 'Z' + 1) {
					acc = 0;
				}
			}
		}
	}
}
