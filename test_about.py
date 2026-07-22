from tkinter import Tk
from photo_selector_toolbox.gui import AboutDialog

root = Tk()
dialog = AboutDialog(root)
root.update_idletasks()
root.destroy()
