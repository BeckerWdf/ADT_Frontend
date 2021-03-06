package org.abapgit.adt.ui.views;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

import org.abapgit.adt.backend.IRepository;
import org.abapgit.adt.backend.IRepositoryService;
import org.abapgit.adt.backend.RepositoryServiceFactory;
import org.abapgit.adt.ui.AbapGitUIPlugin;
import org.abapgit.adt.ui.wizards.AbapGitWizard;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.statushandlers.StatusManager;

import com.sap.adt.destinations.ui.logon.AdtLogonServiceUIFactory;
import com.sap.adt.project.ui.util.ProjectUtil;
import com.sap.adt.tools.core.project.AdtProjectServiceFactory;

public class MainView extends ViewPart {

	public static final String ID = "org.abapgit.adt.ui.views.MainView";

	private TableViewer viewer;
	// private Action actionPull, actionDelete;
	private Action actionSync, actionWizard;
	private ISelection lastSelection;

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);

		ISelection selection = getSite().getPage().getSelection();
		if (selection instanceof ISelection) {
			lastSelection = selection;
		}
	}

	private ISelectionListener selectionListener = new ISelectionListener() {
		private boolean isUpdatingSelection = false;

		@Override
		public void selectionChanged(IWorkbenchPart part, ISelection selection) {
			if (isUpdatingSelection)
				return;

			try {
				isUpdatingSelection = true;
				if (MainView.this == part)
					return;

				if (selection instanceof IStructuredSelection) {
					IStructuredSelection structSelection = (IStructuredSelection) selection;
					// Always take the first element - this is not intended to work with multiple
					// selection
					// Also, hang on to this selection for future use in case the history view is
					// not visible
					lastSelection = structSelection;

					if (!checkIfPageIsVisible()) {
						return;
					}

					showLastSelectedElement();
				}
			} finally {
				isUpdatingSelection = false;
			}
		}
	};

	private IProject lastProject;

	private boolean checkIfPageIsVisible() {
		return getViewSite().getPage().isPartVisible(this);
	}

	private void showLastSelectedElement() {
		IProject currentProject = ProjectUtil.getActiveAdtCoreProject(lastSelection, null, null, null);
		if (currentProject != lastProject) {
			lastProject = currentProject;
			updateView();
		}

		// reset lastSelectedElement to null to prevent updating history view if it just
		// gets focus
		lastSelection = null;

	}

	/**
	 * The constructor.
	 */

	class ViewLabelProvider extends LabelProvider implements ITableLabelProvider {
		@Override
		public String getColumnText(Object obj, int index) {
			return getText(obj);
		}

		@Override
		public Image getColumnImage(Object obj, int index) {
			return getImage(obj);
		}

		@Override
		public Image getImage(Object obj) {
			return AbapGitUIPlugin.getDefault().getImageDescriptor(ISharedImages.IMG_TOOL_REDO).createImage();
		}

	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize it.
	 */
	@Override
	public void createPartControl(Composite parent) {
		setupViewer(parent);

		makeActions();
		hookContextMenu();
		contributeToActionBars();
		// add listener for selections
		getSite().getPage().addPostSelectionListener(selectionListener);
	}

	private void setupViewer(Composite parent) {
		this.viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		this.viewer.getControl().setEnabled(false);
		Table table = this.viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		createColumns();

		this.viewer.setContentProvider(ArrayContentProvider.getInstance());
	}

	private void createColumns() {
		createTableViewerColumn("Package", 200).setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				IRepository p = (IRepository) element;
				return p.getPackage();
			}
		});

		createTableViewerColumn("URL", 400).setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				IRepository p = (IRepository) element;
				return p.getUrl();
			}
		});

		createTableViewerColumn("Branch", 200).setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				IRepository p = (IRepository) element;
				return p.getBranch();
			}
		});

		createTableViewerColumn("User", 100).setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				IRepository p = (IRepository) element;
				return p.getCreatedBy();
			}
		});

		createTableViewerColumn("First commit timestamp", 150).setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				IRepository p = (IRepository) element;
				return p.getFirstCommit();
			}
		});
	}

	private TableViewerColumn createTableViewerColumn(String title, int bound) {
		TableViewerColumn viewerColumn = new TableViewerColumn(viewer, SWT.NONE);
		TableColumn column = viewerColumn.getColumn();
		column.setText(title);
		column.setWidth(bound);
		column.setResizable(true);
		column.setMoveable(true);
		return viewerColumn;
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager();
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				if (viewer.getStructuredSelection().size() == 1) {
					Object firstElement = viewer.getStructuredSelection().getFirstElement();
					if (firstElement instanceof IRepository) {
						manager.add(new UnlinkAction(lastProject, (IRepository) firstElement));
					}
				}
				// MainView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		this.viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private void contributeToActionBars() {
		IToolBarManager toolBarManager = getViewSite().getActionBars().getToolBarManager();
		toolBarManager.add(actionSync);
		// toolBarManager.add(actionCreate);
		toolBarManager.add(actionWizard);

	}

	private void makeActions() {
		// this.actionPull = new Action() {
		// public void run() {
		// Repository repo = (Repository)
		// viewer.getStructuredSelection().getFirstElement();
		// repo.pull();
		// }
		// };
		// this.actionPull.setText("Pull");
		// this.actionPull.setToolTipText("Pull");
		// this.actionPull.setImageDescriptor(AbapGitUIPlugin.getDefault().getImageDescriptor(ISharedImages.IMG_TOOL_UP));
		//
		// this.actionDelete = new Action() {
		// public void run() {
		// showMessage("delete, todo");
		// }
		// };
		// this.actionDelete.setText("Delete");
		// this.actionDelete.setToolTipText("Delete");
		// this.actionDelete
		// .setImageDescriptor(AbapGitUIPlugin.getDefault().getImageDescriptor(ISharedImages.IMG_ETOOL_DELETE));

		this.actionSync = new Action() {
			public void run() {
				updateView();
				// viewer.setInput(Repository.list());
				// viewer.setInput(getRepoList());
				// viewer.setInput(getRepositories());
			}
		};
		this.actionSync.setText("Syncronize");
		this.actionSync.setToolTipText("Syncronize");
		this.actionSync
				.setImageDescriptor(AbapGitUIPlugin.getDefault().getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED));
		this.actionSync.setEnabled(false);
		// this.actionCreate = new Action() {
		// public void run() {
		// CreateDialog dialog = new CreateDialog(viewer.getControl().getShell());
		// dialog.create();
		//
		// if (dialog.open() == Window.OK) {
		//
		// Repository.create(dialog.getUrl(), dialog.getBranch(), dialog.getDevclass(),
		// dialog.getUser(), dialog.getPwd(), dialog.getTrname());
		//
		//// viewer.setInput(Repository.list());
		// viewer.setInput(getRepoList());
		//// viewer.setInput(getRepositories());
		//
		// }
		// }
		// };
		// this.actionCreate.setText("Create");
		// this.actionCreate.setToolTipText("Create");
		// this.actionCreate
		// .setImageDescriptor(AbapGitUIPlugin.getDefault().getImageDescriptor(ISharedImages.IMG_OBJ_ADD));

		this.actionWizard = new Action() {
			public void run() {

				WizardDialog wizardDialog = new WizardDialog(viewer.getControl().getShell(), new AbapGitWizard());

				if (wizardDialog.open() == Window.OK) {
					updateView();
				} else {
					// System.out.println("Cancel pressed");
					updateView();
				}
			}
		};
		this.actionWizard.setText("Clone Repository");
		this.actionWizard.setToolTipText("Clone Repository");
		this.actionWizard
				.setImageDescriptor(AbapGitUIPlugin.getDefault().getImageDescriptor(ISharedImages.IMG_OBJ_ADD));
		this.actionWizard.setEnabled(false);
	}

	private List<IRepository> getRepositories() {
		if (lastProject == null) {
			return null;
		}

		IStatus logonStatus = AdtLogonServiceUIFactory.createLogonServiceUI().ensureLoggedOn(lastProject);
		if (!logonStatus.isOK()) {
			return null;
		}

		String destinationId = getDestination(lastProject);
		List<IRepository> repos = new LinkedList<>();
		try {
			PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {

				@Override
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					monitor.beginTask("Fetching repositories", IProgressMonitor.UNKNOWN);
					IRepositoryService repoService = RepositoryServiceFactory.createRepositoryService(destinationId,
							monitor);
					if (repoService == null) {
						return;
					}
					repos.addAll(repoService.getRepositories(monitor).getRepositories());
				}
			});

		} catch (InvocationTargetException e) {
			StatusManager.getManager().handle(new Status(IStatus.ERROR, AbapGitUIPlugin.PLUGIN_ID,
					"Error fetching repositories", e.getTargetException()), StatusManager.SHOW);
		} catch (InterruptedException e) {
		}
		return repos;
	}

	private static String getDestination(IProject project) {
		return AdtProjectServiceFactory.createProjectService().getDestinationId(project);
	}

	private void updateView() {
		List<IRepository> repos = getRepositories();

		if (repos != null && !repos.isEmpty()) {
			this.viewer.getControl().setEnabled(true);
			this.actionSync.setEnabled(true);
			this.actionWizard.setEnabled(true);
			this.viewer.setInput(repos);
		} else {
			this.viewer.getControl().setEnabled(false);
			this.actionSync.setEnabled(false);
			this.actionWizard.setEnabled(false);
			this.viewer.setInput(null);
		}
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	@Override
	public void setFocus() {
		this.viewer.getControl().setFocus();

		if (lastSelection != null) {
			showLastSelectedElement();
		}
	}

	@Override
	public void dispose() {
		getSite().getPage().removePostSelectionListener(selectionListener);

		super.dispose();
	}

	private class UnlinkAction extends Action {

		private final IProject project;
		private final IRepository repository;

		public UnlinkAction(IProject project, IRepository repository) {
			this.project = project;
			this.repository = repository;
			setText("Unlink");
		}

		@Override
		public void run() {
			try {
				PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {

					@Override
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						RepositoryServiceFactory.createRepositoryService(getDestination(project), monitor)
								.unlinkRepository(repository.getKey(), monitor);
					}
				});
				updateView();
			} catch (InvocationTargetException e) {
				StatusManager.getManager().handle(new Status(IStatus.ERROR, AbapGitUIPlugin.PLUGIN_ID,
						"Error unlinking repository", e.getTargetException()), StatusManager.SHOW);
			} catch (InterruptedException e) {
			}
		}

	}

}
