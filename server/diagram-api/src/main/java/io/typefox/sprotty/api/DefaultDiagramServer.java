/*
 * Copyright (C) 2017 TypeFox and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.typefox.sprotty.api;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.inject.Inject;

/**
 * The default diagram server implementation. It realizes the same message protocol as the
 * TypeScript class {@code LocalModelSource}.
 */
public class DefaultDiagramServer implements IDiagramServer {
	
	private String clientId;
	
	private SModelRoot currentRoot;
	
	private Map<String, String> options;
	
	private Consumer<ActionMessage> remoteEndpoint;
	
	private IModelUpdateListener modelUpdateListener;
	
	private ILayoutEngine layoutEngine;
	
	private IPopupModelFactory popupModelFactory;
	
	private IDiagramSelectionListener diagramSelectionListener;

	private IDiagramExpansionListener diagramExpansionListener;

	private IDiagramOpenListener diagramOpenListener;
	
	private boolean needsClientLayout = true;
	
	private boolean needsServerLayout = false;
	
	private Set<String> expandedElements = new HashSet<>();

	private Set<String> selectedElements = new HashSet<>();

	public DefaultDiagramServer() {
		currentRoot = new SModelRoot();
		currentRoot.setType("NONE");
		currentRoot.setId("ROOT");
	}
	
	public DefaultDiagramServer(String clientId) {
		this();
		this.clientId = clientId;
	}
	
	@Override
	public IDiagramState getDiagramState() {
		return new DefaultDiagramState(this);
	}

	@Override
	public String getClientId() {
		return clientId;
	}
	
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
	
	@Override
	public Consumer<ActionMessage> getRemoteEndpoint() {
		return remoteEndpoint;
	}
	
	@Override
	public void setRemoteEndpoint(Consumer<ActionMessage> remoteEndpoint) {
		this.remoteEndpoint = remoteEndpoint;
	}
	
	protected IModelUpdateListener getModelUpdateListener() {
		return modelUpdateListener;
	}
	
	@Inject
	public void setModelUpdateListener(IModelUpdateListener listener) {
		this.modelUpdateListener = listener;
	}
	
	protected ILayoutEngine getLayoutEngine() {
		return layoutEngine;
	}
	
	@Inject
	public void setLayoutEngine(ILayoutEngine engine) {
		this.layoutEngine = engine;
	}
	
	protected IPopupModelFactory getPopupModelFactory() {
		return popupModelFactory;
	}
	
	@Inject
	public void setPopupModelFactory(IPopupModelFactory factory) {
		this.popupModelFactory = factory;
	}
	
	protected IDiagramSelectionListener getSelectionListener() {
		return diagramSelectionListener;
	}
	
	@Inject
	public void setSelectionListener(IDiagramSelectionListener listener) {
		this.diagramSelectionListener = listener;
	}
	
	public IDiagramExpansionListener getExpansionListener() {
		return diagramExpansionListener;
	}
	
	@Inject
	public void setExpansionListener(IDiagramExpansionListener diagramExpansionListener) {
		this.diagramExpansionListener = diagramExpansionListener;
	}
	
	public IDiagramOpenListener getOpenListener() {
		return diagramOpenListener;
	}
	
	@Inject
	public void setOpenListener(IDiagramOpenListener diagramOpenListener) {
		this.diagramOpenListener = diagramOpenListener;
	}
	
	@Override
	public void dispatch(Action action) {
		Consumer<ActionMessage> remoteEndpoint = getRemoteEndpoint();
		if (remoteEndpoint != null) {
			remoteEndpoint.accept(new ActionMessage(getClientId(), action));
		}
	}
	
	@Override
	public SModelRoot getModel() {
		return currentRoot;
	}
	
	@Override
	public void setModel(SModelRoot newRoot) {
		if (newRoot == null)
			throw new NullPointerException();
		currentRoot = newRoot;
		submitModel(newRoot, false);
	}
	
	@Override
	public void updateModel(SModelRoot newRoot) {
		if (newRoot == null) {
			submitModel(currentRoot, true);
		} else {
			if (needsServerLayout(newRoot)) {
				LayoutUtil.copyLayoutData(currentRoot, newRoot);
			}
			currentRoot = newRoot;
			submitModel(newRoot, true);
		}
	}
	
	@Override
	public Map<String, String> getOptions() {
		if (options == null) {
			options = new LinkedHashMap<>();
		}
		return options;
	}
	
	protected void setOptions(Map<String, String> options) {
		this.options = new LinkedHashMap<>(options);
	}
	
	/**
	 * Whether the client needs to compute the layout of parts of the model. This affects the behavior or
	 * {@link #submitModel(SModelRoot, boolean)}.
	 * 
	 * <p>The default implementation returns the value configured with {@link #setNeedsClientLayout(boolean)},
	 * but this can be overridden to determine the value depending on the given model. The initial value
	 * is {@code true}.</p>
	 */
	protected boolean needsClientLayout(SModelRoot root) {
		return needsClientLayout;
	}
	
	public void setNeedsClientLayout(boolean value) {
		this.needsClientLayout = value;
	}
	
	/**
	 * Whether the server needs to compute the layout of parts of the model. The layout is computed with
	 * the layout engine configured with {@link #setLayoutEngine(ILayoutEngine)}, so returning {@code true}
	 * here makes sense only if such an engine is available.
	 * 
	 * <p>The default implementation returns the value configured with {@link #setNeedsServerLayout(boolean)},
	 * but this can be overridden to determine the value depending on the given model. The initial value
	 * is {@code false}.</p>
	 */
	protected boolean needsServerLayout(SModelRoot root) {
		return needsServerLayout;
	}
	
	public void setNeedsServerLayout(boolean value) {
		this.needsServerLayout = value;
	}
	
	/**
	 * Submit a new or updated model to the client. If client layout is required, a {@link RequestBoundsAction}
	 * is sent, otherwise either a {@link SetModelAction} or an {@link UpdateModelAction} is sent depending on
	 * the {@code update} parameter.
	 */
	protected void submitModel(SModelRoot newRoot, boolean update) {
		IModelUpdateListener listener = getModelUpdateListener();
		if (needsClientLayout(newRoot)) {
			dispatch(new RequestBoundsAction(newRoot));
			if (!needsServerLayout(newRoot) && listener != null) {
				// In this case the client won't send us the computed bounds, so we trigger the listener immediately
				listener.modelSubmitted(newRoot, this);
			}
		} else {
			if (needsServerLayout(newRoot)) {
				ILayoutEngine layoutEngine = getLayoutEngine();
				if (layoutEngine != null) {
					layoutEngine.layout(newRoot);
				}
			}
			if (update) {
				dispatch(new UpdateModelAction(newRoot));
			} else {
				dispatch(new SetModelAction(newRoot));
			}
			if (listener != null) {
				listener.modelSubmitted(newRoot, this);
			}
		}
	}
	
	@Override
	public void accept(ActionMessage message) {
		String clientId = getClientId();
		if (clientId != null && clientId.equals(message.getClientId())) {
			Action action = message.getAction();
			switch (action.getKind()) {
				case RequestModelAction.KIND:
					handle((RequestModelAction) action);
					break;
				case RequestPopupModelAction.KIND:
					handle((RequestPopupModelAction) action);
					break;
				case ComputedBoundsAction.KIND:
					handle((ComputedBoundsAction) action);
					break;
				case SelectAction.KIND:
					handle((SelectAction) action);
					break;
				case CollapseExpandAction.KIND:
					handle((CollapseExpandAction) action);
					break;
				case OpenAction.KIND:
					handle((OpenAction) action);
					break;
			}
		}
	}
	
	/**
	 * Called when a {@code RequestModelAction} is received.
	 */
	protected void handle(RequestModelAction request) {
		if (request.getOptions() != null) {
			setOptions(request.getOptions());
		}
		SModelRoot model = getModel();
		if (model != null) {
			submitModel(model, false);
		}
	}
	
	/**
	 * Called when a {@code ComputedBoundsAction} is received.
	 */
	protected void handle(ComputedBoundsAction computedBounds) {
		SModelRoot model = getModel();
		if (model != null) {
			LayoutUtil.applyBounds(model, computedBounds);
			if (needsServerLayout(model)) {
				ILayoutEngine layoutEngine = getLayoutEngine();
				if (layoutEngine != null) {
					layoutEngine.layout(model);
				}
			}
			dispatch(new UpdateModelAction(model));
			IModelUpdateListener listener = getModelUpdateListener();
			if (listener != null) {
				listener.modelSubmitted(model, this);
			}
		}
	}
	
	/**
	 * Called when a {@code RequestPopupModelAction} is received.
	 */
	protected void handle(RequestPopupModelAction request) {
		SModelRoot model = getModel();
		SModelElement element = SModelIndex.find(model, request.getElementId());
		IPopupModelFactory factory = getPopupModelFactory();
		if (factory != null) {
			SModelRoot popupModel = factory.createPopupModel(element, request, this);
			if (popupModel != null) {
				dispatch(new SetPopupModelAction(popupModel));
			}
		}
	}
	
	/**
	 * Called when a {@code SelectAction} is received.
	 */
	protected void handle(SelectAction action) {
		if (action.getSelectAll() == Boolean.TRUE)
			new SModelIndex(currentRoot).allIds().forEach(id -> selectedElements.add(id));
		if (action.getDeselectAll() == Boolean.TRUE)
			selectedElements.clear();
		if (action.getDeselectedElementsIDs() != null)
			selectedElements.removeAll(action.getDeselectedElementsIDs());
		if (action.getSelectedElementsIDs() != null)
			selectedElements.addAll(action.getSelectedElementsIDs());

		IDiagramSelectionListener selectionListener = getSelectionListener();
		if (selectionListener != null) {
			selectionListener.selectionChanged(action, this);
		}
	}
	
	/**
	 * Called when a {@code CollapseExpandAction} is received.
	 */
	protected void handle(CollapseExpandAction action) {
		if (action.getCollapseIds() != null)
			expandedElements.removeAll(action.getCollapseIds());
		if (action.getExpandIds() != null)
			expandedElements.addAll(action.getExpandIds());
		IDiagramExpansionListener expansionListener = getExpansionListener();
		if (expansionListener != null) {
			expansionListener.expansionChanged(action, this);
		}
	}
	
	/**
	 * Called when a {@code OpenAction} is received.
	 */
	protected void handle(OpenAction action) {
		IDiagramOpenListener openListener = getOpenListener();
		if (openListener != null) {
			openListener.elementOpened(action, this);
		}
	}
	
	public static class DefaultDiagramState implements IDiagramState {

		private DefaultDiagramServer server;

		DefaultDiagramState(DefaultDiagramServer server) {
			this.server = server;
		}
		
		@Override
		public Map<String, String> getOptions() {
			return server.options;
		}
		
		@Override
		public String getClientId() {
			return server.clientId;
		}
		
		@Override
		public SModelRoot getCurrentModel() {
			return server.currentRoot;
		}
		
		@Override
		public Set<String> getExpandedElements() {
			return server.expandedElements;
		}
		
		@Override
		public Set<String> getSelectedElements() {
			return server.selectedElements;
		}
	}
}
