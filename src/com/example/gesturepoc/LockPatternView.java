package com.example.gesturepoc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 密码手势控件
 */
public class LockPatternView extends View {

	private  final  int COLOR_CORRECT = Color.rgb(0x17, 0x5C, 0xB4);
	private Paint mPaint = new Paint();
	private Paint mPathPaint = new Paint();

	/**
	 * This can be used to avoid updating the display for very small motions or
	 * noisy panels. It didn't seem to have much impact on the devices tested,
	 * so currently set to 0.
	 */
	private static final float DRAG_THRESHHOLD = 0.0f;

	private OnPatternListener mOnPatternListener;
	private ArrayList<Cell> mPattern = new ArrayList<Cell>(9);

	/**
	 * Lookup table for the circles of the pattern we are currently drawing.
	 * This will be the cells of the complete pattern unless we are animating,
	 * in which case we use this to hold the cells we are drawing for the in
	 * progress animation.
	 */
	private boolean[][] mPatternDrawLookup = new boolean[3][3];

	/**
	 * the in progress point: - during interaction: where the user's finger is -
	 * during animation: the current tip of the animating line
	 */
	private float mInProgressX = -1;
	private float mInProgressY = -1;

	private DisplayMode mPatternDisplayMode = DisplayMode.Correct;
	private boolean mInputEnabled = true;
	private boolean mInStealthMode = false; // 是否显示手势轨迹
	private boolean mEnableHapticFeedback = false; // 是否有声音
	private boolean mPatternInProgress = false;

	private float mDiameterFactor = 0.10f; // TODO: move to attrs
	private final int mStrokeAlpha = 128;
	private float mHitFactor = 0.4f; // 决定什么位置的点能够选中宫格，经测试太大、太小都不合适

	private float mSquareWidth;
	private float mSquareHeight;

	private final Path mCurrentPath = new Path();
	private final Rect mInvalidate = new Rect();
	private final Rect mTmpInvalidateRect = new Rect();

	/**
	 * Represents a cell in the 3 X 3 matrix of the unlock pattern view.
	 */
	public static class Cell {
		int row;
		int column;

		// keep # objects limited to 9
		static Cell[][] sCells = new Cell[3][3];
		static {
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 3; j++) {
					sCells[i][j] = new Cell(i, j);
				}
			}
		}

		/**
		 * @param row
		 *            The row of the cell.
		 * @param column
		 *            The column of the cell.
		 */
		private Cell(int row, int column) {
			checkRange(row, column);
			this.row = row;
			this.column = column;
		}

		public int getRow() {
			return row;
		}

		public int getColumn() {
			return column;
		}

		/**
		 * @param row
		 *            The row of the cell.
		 * @param column
		 *            The column of the cell.
		 */
		public static synchronized Cell of(int row, int column) {
			checkRange(row, column);
			return sCells[row][column];
		}

		private static void checkRange(int row, int column) {
			if (row < 0 || row > 2) {
				throw new IllegalArgumentException("row must be in range 0-2");
			}
			if (column < 0 || column > 2) {
				throw new IllegalArgumentException(
						"column must be in range 0-2");
			}
		}

		public String toString() {
			return "(row=" + row + ",clmn=" + column + ")";
		}
	}

	/**
	 * How to display the current pattern.
	 */
	public enum DisplayMode {

		/**
		 * The pattern drawn is correct (i.e draw it in a friendly color)
		 */
		Correct,

		/**
		 * The pattern is wrong (i.e draw a foreboding color)
		 */
		Wrong
	}

	/**
	 * The call back interface for detecting patterns entered by the user.
	 */
	public static interface OnPatternListener {

		/**
		 * A new pattern has begun.
		 */
		void onPatternStart();

		/**
		 * The pattern was cleared.
		 */
		void onPatternCleared();

		/**
		 * The user extended the pattern currently being drawn by one cell.
		 * 
		 * @param pattern
		 *            The pattern with newly added cell.
		 */
		void onPatternCellAdded(List<Cell> pattern);

		/**
		 * A pattern was detected from the user.
		 * 
		 * @param pattern
		 *            The pattern.
		 */
		void onPatternDetected(List<Cell> pattern);
	}

	public LockPatternView(Context context) {
		this(context, null);
	}

	public LockPatternView(Context context, AttributeSet attrs) {
		super(context, attrs);

		setClickable(true);

		mPathPaint.setAntiAlias(true);
		mPathPaint.setDither(true);
		mPathPaint.setColor(COLOR_CORRECT);
		mPathPaint.setAlpha(mStrokeAlpha);
		mPathPaint.setStyle(Paint.Style.STROKE);
		mPathPaint.setStrokeJoin(Paint.Join.ROUND);
		mPathPaint.setStrokeCap(Paint.Cap.ROUND);

	}

	/**
	 * @return Whether the view is in stealth mode.
	 */
	public boolean isInStealthMode() {
		return mInStealthMode;
	}

	/**
	 * @return Whether the view has tactile feedback enabled.
	 */
	public boolean isTactileFeedbackEnabled() {
		return mEnableHapticFeedback;
	}

	/**
	 * Set whether the view is in stealth mode. If true, there will be no
	 * visible feedback as the user enters the pattern.
	 * 
	 * @param inStealthMode
	 *            Whether in stealth mode.
	 */
	public void setInStealthMode(boolean inStealthMode) {
		mInStealthMode = inStealthMode;
	}

	/**
	 * Set whether the view will use tactile feedback. If true, there will be
	 * tactile feedback as the user enters the pattern.
	 * 
	 * @param tactileFeedbackEnabled
	 *            Whether tactile feedback is enabled
	 */
	public void setTactileFeedbackEnabled(boolean tactileFeedbackEnabled) {
		mEnableHapticFeedback = tactileFeedbackEnabled;
	}

	/**
	 * Set the call back for pattern detection.
	 * 
	 * @param onPatternListener
	 *            The call back.
	 */
	public void setOnPatternListener(OnPatternListener onPatternListener) {
		mOnPatternListener = onPatternListener;
	}

	/**
	 * Set the pattern explicitely (rather than waiting for the user to input a
	 * pattern).
	 * 
	 * @param displayMode
	 *            How to display the pattern.
	 * @param pattern
	 *            The pattern.
	 */
	public void setPattern(DisplayMode displayMode, List<Cell> pattern) {
		mPattern.clear();
		mPattern.addAll(pattern);
		clearPatternDrawLookup();
		for (Cell cell : pattern) {
			mPatternDrawLookup[cell.getRow()][cell.getColumn()] = true;
		}

		setDisplayMode(displayMode);
	}

	/**
	 * Set the display mode of the current pattern. This can be useful, for
	 * instance, after detecting a pattern to tell this view whether change the
	 * in progress result to correct or wrong.
	 * 
	 * @param displayMode
	 *            The display mode.
	 */
	public void setDisplayMode(DisplayMode displayMode) {
		mPatternDisplayMode = displayMode;
		invalidate();
	}

	private void notifyCellAdded() {
		sendAccessEvent(R.string.lockscreen_access_pattern_cell_added);
		if (mOnPatternListener != null) {
			mOnPatternListener.onPatternCellAdded(mPattern);
		}
	}

	private void notifyPatternStarted() {
		sendAccessEvent(R.string.lockscreen_access_pattern_start);
		if (mOnPatternListener != null) {
			mOnPatternListener.onPatternStart();
		}
	}

	private void notifyPatternDetected() {
		sendAccessEvent(R.string.lockscreen_access_pattern_detected);
		if (mOnPatternListener != null) {
			mOnPatternListener.onPatternDetected(mPattern);
		}
	}

	private void notifyPatternCleared() {
		sendAccessEvent(R.string.lockscreen_access_pattern_cleared);
		if (mOnPatternListener != null) {
			mOnPatternListener.onPatternCleared();
		}
	}

	/**
	 * Clear the pattern.
	 */
	public void clearPattern() {
		resetPattern();
	}

	/**
	 * Reset all pattern state.
	 */
	private void resetPattern() {
		mPattern.clear();
		clearPatternDrawLookup();
		mPatternDisplayMode = DisplayMode.Correct;
		invalidate();
	}

	/**
	 * Clear the pattern lookup table.
	 */
	private void clearPatternDrawLookup() {
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				mPatternDrawLookup[i][j] = false;
			}
		}
	}

	/**
	 * Disable input (for instance when displaying a message that will timeout
	 * so user doesn't get view into messy state).
	 */
	public void disableInput() {
		mInputEnabled = false;
	}

	/**
	 * Enable input.
	 */
	public void enableInput() {
		mInputEnabled = true;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		final int width = w - getPaddingLeft() - getPaddingRight();
		mSquareWidth = width / 3.0f;

		final int height = h - getPaddingTop() - getPaddingBottom();
		mSquareHeight = height / 3.0f;
	}

	private int resolveMeasured(int measureSpec, int desired) {
		int result = 0;
		int specSize = MeasureSpec.getSize(measureSpec);
		switch (MeasureSpec.getMode(measureSpec)) {
		case MeasureSpec.UNSPECIFIED:
			result = desired;
			break;
		case MeasureSpec.AT_MOST:
			result = Math.max(specSize, desired);
			break;
		case MeasureSpec.EXACTLY:
		default:
			result = specSize;
		}
		return result;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		final int minimumWidth = getSuggestedMinimumWidth();
		final int minimumHeight = getSuggestedMinimumHeight();
		int viewWidth = resolveMeasured(widthMeasureSpec, minimumWidth);
		int viewHeight = resolveMeasured(heightMeasureSpec, minimumHeight);
		viewWidth = viewHeight = Math.min(viewWidth, viewHeight);
		setMeasuredDimension(viewWidth, viewHeight);
	}

	/**
	 * Determines whether the point x, y will add a new point to the current
	 * pattern (in addition to finding the cell, also makes heuristic choices
	 * such as filling in gaps based on current pattern).
	 * 
	 * @param x
	 *            The x coordinate.
	 * @param y
	 *            The y coordinate.
	 */
	private Cell detectAndAddHit(float x, float y) {
		final Cell cell = checkForNewHit(x, y);
		if (cell != null) {

			// check for gaps in existing pattern
			Cell fillInGapCell = null;
			final ArrayList<Cell> pattern = mPattern;
			if (!pattern.isEmpty()) {
				final Cell lastCell = pattern.get(pattern.size() - 1);
				int dRow = cell.row - lastCell.row;
				int dColumn = cell.column - lastCell.column;

				int fillInRow = lastCell.row;
				int fillInColumn = lastCell.column;

				if (Math.abs(dRow) == 2 && Math.abs(dColumn) != 1) {
					fillInRow = lastCell.row + ((dRow > 0) ? 1 : -1);
				}

				if (Math.abs(dColumn) == 2 && Math.abs(dRow) != 1) {
					fillInColumn = lastCell.column + ((dColumn > 0) ? 1 : -1);
				}

				fillInGapCell = Cell.of(fillInRow, fillInColumn);
			}

			if (fillInGapCell != null
					&& !mPatternDrawLookup[fillInGapCell.row][fillInGapCell.column]) {
				addCellToPattern(fillInGapCell);
			}
			addCellToPattern(cell);
			if (mEnableHapticFeedback) {
				performHapticFeedback(
						HapticFeedbackConstants.VIRTUAL_KEY,
						HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
								| HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
			}
			return cell;
		}
		return null;
	}

	private void addCellToPattern(Cell newCell) {
		mPatternDrawLookup[newCell.getRow()][newCell.getColumn()] = true;
		mPattern.add(newCell);
		notifyCellAdded();
	}

	// helper method to find which cell a point maps to
	private Cell checkForNewHit(float x, float y) {

		final int rowHit = getRowHit(y);
		if (rowHit < 0) {
			return null;
		}
		final int columnHit = getColumnHit(x);
		if (columnHit < 0) {
			return null;
		}

		if (mPatternDrawLookup[rowHit][columnHit]) {
			return null;
		}
		return Cell.of(rowHit, columnHit);
	}

	/**
	 * Helper method to find the row that y falls into.
	 * 
	 * @param y
	 *            The y coordinate
	 * @return The row that y falls in, or -1 if it falls in no row.
	 */
	private int getRowHit(float y) {

		final float squareHeight = mSquareHeight;
		float hitSize = squareHeight * mHitFactor;

		float offset = getPaddingTop() + (squareHeight - hitSize) / 2f;
		for (int i = 0; i < 3; i++) {

			final float hitTop = offset + squareHeight * i;
			if (y >= hitTop && y <= hitTop + hitSize) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Helper method to find the column x fallis into.
	 * 
	 * @param x
	 *            The x coordinate.
	 * @return The column that x falls in, or -1 if it falls in no column.
	 */
	private int getColumnHit(float x) {
		final float squareWidth = mSquareWidth;
		float hitSize = squareWidth * mHitFactor;

		float offset = getPaddingLeft() + (squareWidth - hitSize) / 2f;
		for (int i = 0; i < 3; i++) {

			final float hitLeft = offset + squareWidth * i;
			if (x >= hitLeft && x <= hitLeft + hitSize) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean onHoverEvent(MotionEvent event) {
		AccessibilityManager accessibilityManager = (AccessibilityManager) getContext()
				.getSystemService(Context.ACCESSIBILITY_SERVICE);
		if (accessibilityManager.isTouchExplorationEnabled()) {
			final int action = event.getAction();
			switch (action) {
			case MotionEvent.ACTION_HOVER_ENTER:
				event.setAction(MotionEvent.ACTION_DOWN);
				break;
			case MotionEvent.ACTION_HOVER_MOVE:
				event.setAction(MotionEvent.ACTION_MOVE);
				break;
			case MotionEvent.ACTION_HOVER_EXIT:
				event.setAction(MotionEvent.ACTION_UP);
				break;
			}
			onTouchEvent(event);
			event.setAction(action);
		}
		return super.onHoverEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!mInputEnabled || !isEnabled()) {
			return false;
		}

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			handleActionDown(event);
			return true;
		case MotionEvent.ACTION_UP:
			handleActionUp(event);
			return true;
		case MotionEvent.ACTION_MOVE:
			handleActionMove(event);
			return true;
		case MotionEvent.ACTION_CANCEL:
			if (mPatternInProgress) {
				mPatternInProgress = false;
				resetPattern();
				notifyPatternCleared();
			}
			return true;
		}
		return false;
	}

	private void handleActionMove(MotionEvent event) {
		// Handle all recent motion events so we don't skip any cells even when
		// the device
		// is busy...
		final float radius = (mSquareWidth * mDiameterFactor * 0.5f);
		final int historySize = event.getHistorySize();
		mTmpInvalidateRect.setEmpty();
		boolean invalidateNow = false;
		for (int i = 0; i < historySize + 1; i++) {
			final float x = i < historySize ? event.getHistoricalX(i) : event
					.getX();
			final float y = i < historySize ? event.getHistoricalY(i) : event
					.getY();
			Cell hitCell = detectAndAddHit(x, y);
			final int patternSize = mPattern.size();
			if (hitCell != null && patternSize == 1) {
				mPatternInProgress = true;
				notifyPatternStarted();
			}
			// note current x and y for rubber banding of in progress patterns
			final float dx = Math.abs(x - mInProgressX);
			final float dy = Math.abs(y - mInProgressY);
			if (dx > DRAG_THRESHHOLD || dy > DRAG_THRESHHOLD) {
				invalidateNow = true;
			}

			if (mPatternInProgress && patternSize > 0) {
				final ArrayList<Cell> pattern = mPattern;
				final Cell lastCell = pattern.get(patternSize - 1);
				float lastCellCenterX = getCenterXForColumn(lastCell.column);
				float lastCellCenterY = getCenterYForRow(lastCell.row);

				// Adjust for drawn segment from last cell to (x,y). Radius
				// accounts for line width.
				float left = Math.min(lastCellCenterX, x) - radius;
				float right = Math.max(lastCellCenterX, x) + radius;
				float top = Math.min(lastCellCenterY, y) - radius;
				float bottom = Math.max(lastCellCenterY, y) + radius;

				// Invalidate between the pattern's new cell and the pattern's
				// previous cell
				if (hitCell != null) {
					final float width = mSquareWidth * 0.5f;
					final float height = mSquareHeight * 0.5f;
					final float hitCellCenterX = getCenterXForColumn(hitCell.column);
					final float hitCellCenterY = getCenterYForRow(hitCell.row);

					left = Math.min(hitCellCenterX - width, left);
					right = Math.max(hitCellCenterX + width, right);
					top = Math.min(hitCellCenterY - height, top);
					bottom = Math.max(hitCellCenterY + height, bottom);
				}

				// Invalidate between the pattern's last cell and the previous
				// location
				mTmpInvalidateRect.union(Math.round(left), Math.round(top),
						Math.round(right), Math.round(bottom));
			}
		}
		mInProgressX = event.getX();
		mInProgressY = event.getY();

		// To save updates, we only invalidate if the user moved beyond a
		// certain amount.
		if (invalidateNow) {
			mInvalidate.union(mTmpInvalidateRect);
			invalidate(mInvalidate);
			mInvalidate.set(mTmpInvalidateRect);
		}
	}

	private void sendAccessEvent(int resId) {
		announceForAccessibility(getContext().getString(resId));
	}

	private void handleActionUp(MotionEvent event) {
		// report pattern detected
		if (!mPattern.isEmpty()) {
			mPatternInProgress = false;
			notifyPatternDetected();
			invalidate();
		}
	}

	private void handleActionDown(MotionEvent event) {
		resetPattern();
		final float x = event.getX();
		final float y = event.getY();
		final Cell hitCell = detectAndAddHit(x, y);
		if (hitCell != null) {
			mPatternInProgress = true;
			mPatternDisplayMode = DisplayMode.Correct;
			notifyPatternStarted();
		} else if (mPatternInProgress) {
			mPatternInProgress = false;
			notifyPatternCleared();
		}
		if (hitCell != null) {
			final float startX = getCenterXForColumn(hitCell.column);
			final float startY = getCenterYForRow(hitCell.row);

			final float widthOffset = mSquareWidth / 2f;
			final float heightOffset = mSquareHeight / 2f;

			invalidate((int) (startX - widthOffset),
					(int) (startY - heightOffset),
					(int) (startX + widthOffset), (int) (startY + heightOffset));
		}
		mInProgressX = x;
		mInProgressY = y;
	}

	private float getCenterXForColumn(int column) {
		return getPaddingLeft() + column * mSquareWidth + mSquareWidth / 2f;
	}

	private float getCenterYForRow(int row) {
		return getPaddingTop() + row * mSquareHeight + mSquareHeight / 2f;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		final ArrayList<Cell> pattern = mPattern;
		final int count = pattern.size();
		final boolean[][] drawLookup = mPatternDrawLookup;

		final float squareWidth = mSquareWidth;
		final float squareHeight = mSquareHeight;

		float radius = (squareWidth * mDiameterFactor * 0.5f);
		mPathPaint.setStrokeWidth(radius);

		final Path currentPath = mCurrentPath;
		currentPath.rewind();

		// draw the circles
		final int paddingTop = getPaddingTop();
		final int paddingLeft = getPaddingLeft();

		for (int i = 0; i < 3; i++) {
			float topY = paddingTop + i * squareHeight;
			// float centerY = getPaddingTop() + i * mSquareHeight +
			// (mSquareHeight / 2);
			for (int j = 0; j < 3; j++) {
				float leftX = paddingLeft + j * squareWidth;
				drawCircle(canvas, (int) leftX, (int) topY, drawLookup[i][j]);
			}
		}

		// TODO: the path should be created and cached every time we hit-detect
		// a cell
		// only the last segment of the path should be computed here
		// draw the path of the pattern (unless the user is in progress, and
		// we are in stealth mode)
		final boolean drawPath = (!mInStealthMode || mPatternDisplayMode == DisplayMode.Wrong);

		// draw the arrows associated with the path (unless the user is in
		// progress, and
		// we are in stealth mode)
		if (drawPath) {
			boolean anyCircles = false;
			for (int i = 0; i < count; i++) {
				Cell cell = pattern.get(i);

				// only draw the part of the pattern stored in
				// the lookup table (this is only different in the case
				// of animation).
				if (!drawLookup[cell.row][cell.column]) {
					break;
				}
				anyCircles = true;

				float centerX = getCenterXForColumn(cell.column);
				float centerY = getCenterYForRow(cell.row);
				if (i == 0) {
					currentPath.moveTo(centerX, centerY);
				} else {
					if (mPatternDisplayMode == DisplayMode.Wrong) {
						mPathPaint.setColor(Color.RED);
					}

					// 原生控件连接线是从圆心到圆心，这里修改连接线为从圆的边上到圆的边上
					// currentPath.lineTo(centerX, centerY);
					Cell preCell = pattern.get(i - 1);
					float preCenterX = getCenterXForColumn(preCell.column);
					float preCenterY = getCenterYForRow(preCell.row);
					float distance = LockPatternUtils
							.getDistanceBetweenTwoPoints(preCenterX,
									preCenterY, centerX, centerY);
					float cellRadius = mSquareHeight / 5 + 5;

					float x1 = cellRadius / distance * (centerX - preCenterX)
							+ preCenterX;
					float y1 = cellRadius / distance * (centerY - preCenterY)
							+ preCenterY;
					float x2 = (distance - cellRadius) / distance
							* (centerX - preCenterX) + preCenterX;
					float y2 = (distance - cellRadius) / distance
							* (centerY - preCenterY) + preCenterY;
					canvas.drawLine(x1, y1, x2, y2, mPathPaint);
					currentPath.moveTo(centerX, centerY);

				}
			}

			// add last in progress section
			if (mPatternInProgress && anyCircles) {

				// 从圆心连线改为从边上连线，大圆内不会出现线
				Cell cell = pattern.get(count - 1);
				float centerX = getCenterXForColumn(cell.column);
				float centerY = getCenterYForRow(cell.row);
				float distance = LockPatternUtils.getDistanceBetweenTwoPoints(
						mInProgressX, mInProgressY, centerX, centerY);
				float cellRadius = mSquareHeight / 5 + 5;
				float x1 = cellRadius / distance * (mInProgressX - centerX)
						+ centerX;
				float y1 = cellRadius / distance * (mInProgressY - centerY)
						+ centerY;
				currentPath.moveTo(x1, y1);

				mPathPaint.setColor(COLOR_CORRECT);
				currentPath.lineTo(mInProgressX, mInProgressY);
			}
			canvas.drawPath(currentPath, mPathPaint);
		}

	}

	private void drawCircle(Canvas canvas, int leftX, int topY,
			boolean partOfPattern) {

		final float squareWidth = mSquareWidth;
		final float squareHeight = mSquareHeight;

		if (!partOfPattern
				|| (mInStealthMode && mPatternDisplayMode != DisplayMode.Wrong)) {
			// unselected circle
			mPaint.setStyle(Style.FILL);
			mPaint.setColor(Color.GRAY);
			canvas.drawCircle(leftX + squareWidth / 2, topY + squareHeight / 2,
					squareHeight / 10, mPaint);
		} else if (mPatternInProgress) {
			// user is in middle of drawing a pattern
			mPaint.setStyle(Style.FILL);
			mPaint.setColor(COLOR_CORRECT);
			canvas.drawCircle(leftX + squareWidth / 2, topY + squareHeight / 2,
					squareHeight / 10, mPaint);

			mPaint.setStyle(Style.STROKE);
			mPaint.setStrokeWidth(5);
			mPaint.setColor(COLOR_CORRECT);
			canvas.drawCircle(leftX + squareWidth / 2, topY + squareHeight / 2,
					squareHeight / 5, mPaint);
		} else if (mPatternDisplayMode == DisplayMode.Wrong) {
			// the pattern is wrong
			mPaint.setStyle(Style.FILL);
			mPaint.setColor(Color.RED);
			canvas.drawCircle(leftX + squareWidth / 2, topY + squareHeight / 2,
					squareHeight / 10, mPaint);

			mPaint.setStyle(Style.STROKE);
			mPaint.setColor(Color.RED);
			canvas.drawCircle(leftX + squareWidth / 2, topY + squareHeight / 2,
					squareHeight / 5, mPaint);
		} else if (mPatternDisplayMode == DisplayMode.Correct) {
			// the pattern is correct
			mPaint.setStyle(Style.FILL);
			mPaint.setColor(COLOR_CORRECT);
			canvas.drawCircle(leftX + squareWidth / 2, topY + squareHeight / 2,
					squareHeight / 10, mPaint);

			mPaint.setStyle(Style.STROKE);
			mPaint.setColor(COLOR_CORRECT);
			canvas.drawCircle(leftX + squareWidth / 2, topY + squareHeight / 2,
					squareHeight / 5, mPaint);
		} else {
			throw new IllegalStateException("unknown display mode "
					+ mPatternDisplayMode);
		}

	}

	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		return new SavedState(superState, patternToString(mPattern),
				mPatternDisplayMode.ordinal(), mInputEnabled, mInStealthMode,
				mEnableHapticFeedback);
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		final SavedState ss = (SavedState) state;
		super.onRestoreInstanceState(ss.getSuperState());
		setPattern(DisplayMode.Correct,
				stringToPattern(ss.getSerializedPattern()));
		mPatternDisplayMode = DisplayMode.values()[ss.getDisplayMode()];
		mInputEnabled = ss.isInputEnabled();
		mInStealthMode = ss.isInStealthMode();
		mEnableHapticFeedback = ss.isTactileFeedbackEnabled();
	}

	/**
	 * Deserialize a pattern.
	 * 
	 * @param string
	 *            The pattern serialized with {@link #patternToString}
	 * @return The pattern.
	 */
	public static List<LockPatternView.Cell> stringToPattern(String string) {
		List<LockPatternView.Cell> result = new ArrayList<LockPatternView.Cell>();

		final byte[] bytes = string.getBytes();
		for (int i = 0; i < bytes.length; i++) {
			byte b = bytes[i];
			result.add(LockPatternView.Cell.of(b / 3, b % 3));
		}
		return result;
	}

	/**
	 * Serialize a pattern.
	 * 
	 * @param pattern
	 *            The pattern.
	 * @return The pattern in string form.
	 */
	public static String  patternToString(List<LockPatternView.Cell> pattern) {
		if (pattern == null) {
			return "";
		}
		final int patternSize = pattern.size();

		byte[] res = new byte[patternSize];
		for (int i = 0; i < patternSize; i++) {
			LockPatternView.Cell cell = pattern.get(i);
			res[i] = (byte) (cell.getRow() * 3 + cell.getColumn());
		}
		return new String(res);
	}

	/**
	 * The parecelable for saving and restoring a lock pattern view.
	 */
	private static class SavedState extends BaseSavedState {

		private final String mSerializedPattern;
		private final int mDisplayMode;
		private final boolean mInputEnabled;
		private final boolean mInStealthMode;
		private final boolean mTactileFeedbackEnabled;

		/**
		 * Constructor called from {@link LockPatternView#onSaveInstanceState()}
		 */
		private SavedState(Parcelable superState, String serializedPattern,
				int displayMode, boolean inputEnabled, boolean inStealthMode,
				boolean tactileFeedbackEnabled) {
			super(superState);
			mSerializedPattern = serializedPattern;
			mDisplayMode = displayMode;
			mInputEnabled = inputEnabled;
			mInStealthMode = inStealthMode;
			mTactileFeedbackEnabled = tactileFeedbackEnabled;
		}

		/**
		 * Constructor called from {@link #CREATOR}
		 */
		private SavedState(Parcel in) {
			super(in);
			mSerializedPattern = in.readString();
			mDisplayMode = in.readInt();
			mInputEnabled = (Boolean) in.readValue(null);
			mInStealthMode = (Boolean) in.readValue(null);
			mTactileFeedbackEnabled = (Boolean) in.readValue(null);
		}

		public String getSerializedPattern() {
			return mSerializedPattern;
		}

		public int getDisplayMode() {
			return mDisplayMode;
		}

		public boolean isInputEnabled() {
			return mInputEnabled;
		}

		public boolean isInStealthMode() {
			return mInStealthMode;
		}

		public boolean isTactileFeedbackEnabled() {
			return mTactileFeedbackEnabled;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeString(mSerializedPattern);
			dest.writeInt(mDisplayMode);
			dest.writeValue(mInputEnabled);
			dest.writeValue(mInStealthMode);
			dest.writeValue(mTactileFeedbackEnabled);
		}

	}

}
