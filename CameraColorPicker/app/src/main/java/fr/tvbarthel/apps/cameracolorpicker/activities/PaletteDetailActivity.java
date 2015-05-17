package fr.tvbarthel.apps.cameracolorpicker.activities;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import fr.tvbarthel.apps.cameracolorpicker.R;
import fr.tvbarthel.apps.cameracolorpicker.adapters.ColorItemAdapter;
import fr.tvbarthel.apps.cameracolorpicker.data.ColorItem;
import fr.tvbarthel.apps.cameracolorpicker.data.Palette;
import fr.tvbarthel.apps.cameracolorpicker.data.Palettes;
import fr.tvbarthel.apps.cameracolorpicker.fragments.DeletePaletteDialogFragment;
import fr.tvbarthel.apps.cameracolorpicker.fragments.EditTextDialogFragment;
import fr.tvbarthel.apps.cameracolorpicker.utils.ClipDatas;
import fr.tvbarthel.apps.cameracolorpicker.views.PaletteView;

/**
 * A simple {@link ActionBarActivity} for displaying a {@link Palette} with its {@link ColorItem}s
 */
public class PaletteDetailActivity extends ActionBarActivity implements DeletePaletteDialogFragment.Callback,
        EditTextDialogFragment.Callback {

    /**
     * A key for passing a color palette as extra.
     */
    protected static final String EXTRA_COLOR_PALETTE = "PaletteDetailActivity.Extras.EXTRA_COLOR_PALETTE";

    /**
     * A key for passing the global visible rect of the clicked color preview clicked.
     */
    protected static final String EXTRA_START_BOUNDS = "PaletteDetailActivity.Extras.EXTRA_START_BOUNDS";

    public static void startWithColorPalette(Context context, Palette palette, View colorPreviewClicked) {
        final boolean isActivity = context instanceof Activity;
        final Rect startBounds = new Rect();
        colorPreviewClicked.getGlobalVisibleRect(startBounds);

        final Intent intent = new Intent(context, PaletteDetailActivity.class);
        intent.putExtra(EXTRA_COLOR_PALETTE, palette);
        intent.putExtra(EXTRA_START_BOUNDS, startBounds);

        if (!isActivity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);

        if (isActivity) {
            ((Activity) context).overridePendingTransition(0, 0);
        }
    }

    /**
     * A {@link PaletteView} for showing the palette preview being translated during the start animation from the clicked position to the right location.
     */
    protected PaletteView mTranslatedPreview;

    /**
     * A {@link PaletteView} for showing the palette preview being scaled during the start animation to show the {@link Palette}.
     */
    protected PaletteView mScaledPreview;

    /**
     * The {@link Palette} being displayed.
     */
    protected Palette mPalette;

    /**
     * The user-visible label for the clip {@link fr.tvbarthel.apps.cameracolorpicker.data.ColorItem}.
     */
    private String mClipColorItemLabel;

    /**
     * A reference to the current {@link android.widget.Toast}.
     * <p/>
     * Used for hiding the current {@link android.widget.Toast} before showing a new one or the activity is paused.
     * {@link }
     */
    private Toast mToast;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_palette_detail);

        mClipColorItemLabel = getString(R.string.color_clip_color_label_hex);

        // ensure correct extras.
        final Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_COLOR_PALETTE) || !intent.hasExtra(EXTRA_START_BOUNDS)) {
            throw new IllegalStateException("Missing extras. Please use startWithColorPalette.");
        }

        // Retrieve the extras.
        mPalette = intent.getParcelableExtra(EXTRA_COLOR_PALETTE);
        final Rect startBounds = intent.getParcelableExtra(EXTRA_START_BOUNDS);
        setTitle(mPalette.getName());

        // Create a rect that will be used to retrieve the stop bounds.
        final Rect stopBounds = new Rect();

        // Find the views.
        mTranslatedPreview = (PaletteView) findViewById(R.id.activity_palette_detail_preview_translating);
        mScaledPreview = (PaletteView) findViewById(R.id.activity_palette_detail_preview_scaling);
        final View listViewShadow = findViewById(R.id.activity_palette_detail_list_view_shadow);
        final ListView listView = (ListView) findViewById(R.id.activity_palette_detail_list_view);
        final ColorItemAdapter adapter = new ColorItemAdapter(this);
        adapter.addAll(mPalette.getColors());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final ColorItem colorItem = adapter.getItem(position);
                ColorDetailActivity.startWithColorItem(view.getContext(), colorItem,
                        view.findViewById(R.id.row_color_item_preview), false);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final ColorItem colorItem = adapter.getItem(position);
                ClipDatas.clipPainText(view.getContext(), mClipColorItemLabel, colorItem.getHexString());
                showToast(R.string.color_clip_success_copy_message);
                return true;
            }
        });

        mTranslatedPreview.setPalette(mPalette);
        mScaledPreview.setPalette(mPalette);

        final ViewTreeObserver viewTreeObserver = mTranslatedPreview.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    mTranslatedPreview.getViewTreeObserver().removeOnPreDrawListener(this);
                    mTranslatedPreview.getGlobalVisibleRect(stopBounds);
                    final int deltaX = startBounds.left - stopBounds.left;
                    final int deltaY = startBounds.top - stopBounds.top;
                    final float scaleRatioX = mTranslatedPreview.getWidth() / (float) mScaledPreview.getWidth();
                    final float scaleRatioY = mTranslatedPreview.getHeight() / (float) mScaledPreview.getHeight();
                    mScaledPreview.setScaleX(scaleRatioX);
                    mScaledPreview.setScaleY(scaleRatioY);
                    mScaledPreview.setVisibility(View.INVISIBLE);
                    final AnimatorSet translationAnimatorSet = new AnimatorSet();
                    translationAnimatorSet.play(ObjectAnimator.ofFloat(mTranslatedPreview, View.TRANSLATION_X, deltaX, 0))
                            .with(ObjectAnimator.ofFloat(mTranslatedPreview, View.TRANSLATION_Y, deltaY, 0));
                    translationAnimatorSet.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            listView.setVisibility(View.VISIBLE);
                            mScaledPreview.setVisibility(View.VISIBLE);
                            listViewShadow.setVisibility(View.VISIBLE);
                            final AnimatorSet scaleAnimatorSet = new AnimatorSet();
                            scaleAnimatorSet.play(ObjectAnimator.ofFloat(mScaledPreview, View.SCALE_X, scaleRatioX, 1f))
                                    .with(ObjectAnimator.ofFloat(mScaledPreview, View.SCALE_Y, scaleRatioY, 1f))
                                    .with(ObjectAnimator.ofFloat(listView, View.ALPHA, 0f, 1f))
                                    .with(ObjectAnimator.ofFloat(listViewShadow, View.ALPHA, 0f, 1f));
                            scaleAnimatorSet.start();
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {
                        }
                    });

                    translationAnimatorSet.start();
                    return true;
                }
            });
        }
    }

    @Override
    protected void onPause() {
        hideToast();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_palette_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.menu_palette_detail_action_delete) {
            DeletePaletteDialogFragment.newInstance(mPalette).show(getSupportFragmentManager(), null);
            return true;
        } else if (id == R.id.menu_palette_detail_action_edit) {
            EditTextDialogFragment.newInstance(0,
                    R.string.activity_palette_detail_edit_palette_name_dialog_title,
                    R.string.activity_palette_detail_edit_palette_name_dialog_positive_action,
                    android.R.string.cancel,
                    R.string.activity_palette_detail_edit_palette_name_dialog_hint,
                    mPalette.getName()).show(getSupportFragmentManager(), null);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPaletteDeletionConfirmed(@NonNull Palette paletteToDelete) {
        if (Palettes.deleteColorPalette(this, paletteToDelete)) {
            finish();
        }
    }

    @Override
    public void onEditTextDialogFragmentPositiveButtonClick(int requestCode, String text) {
        if (!mPalette.getName().equals(text)) {
            mPalette.setName(text);
            if (Palettes.saveColorPalette(this, mPalette)) {
                setTitle(text);
            }
        }
    }

    @Override
    public void onEditTextDialogFragmentNegativeButtonClick(int requestCode) {
        // Nothing to do. The user aborted the edition of the name of the palette.
    }

    /**
     * Hide the current {@link android.widget.Toast}.
     */
    private void hideToast() {
        if (mToast != null) {
            mToast.cancel();
            mToast = null;
        }
    }

    /**
     * Show a toast text message.
     *
     * @param resId The resource id of the string resource to use.
     */
    private void showToast(@StringRes int resId) {
        hideToast();
        mToast = Toast.makeText(this, resId, Toast.LENGTH_SHORT);
        mToast.show();
    }
}