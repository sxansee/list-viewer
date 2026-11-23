using System;
using System.ComponentModel;
using System.Windows;
using System.Windows.Threading;
using Glass.Studio.ViewModels;

namespace Glass.Studio
{
    /// <summary>
    /// Окно курса. Только показывает CourseViewModel: вся логика (выбор урока, подсказки, звёзды, прогресс, проверка решения
    /// настоящей VM) живёт в ядре и проверяется тестами. Окно не модальное — рядом с редактором, где ученик пишет решение.
    /// </summary>
    public partial class CourseWindow : Window
    {
        private readonly CourseViewModel _viewModel;

        public CourseWindow(CourseViewModel viewModel)
        {
            InitializeComponent();
            _viewModel = viewModel;
            DataContext = viewModel;
            viewModel.PropertyChanged += OnViewModelPropertyChanged;
            Closed += (_, _) => viewModel.PropertyChanged -= OnViewModelPropertyChanged;
        }

        private void OnViewModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
        {
            // Новый урок открывается с начала страницы. Иначе при переходе по «Следующий урок →» с конца длинной страницы
            // ScrollViewer сохранял бы прежнее смещение, и следующий урок появлялся бы прокрученным до середины — без теории.
            if (e.PropertyName == nameof(CourseViewModel.LessonTitle))
            {
                LessonScroll.ScrollToTop();
            }
            // Проверка закончилась: показать результат, даже если он ниже видимой области (страница длиннее окна).
            else if (e.PropertyName == nameof(CourseViewModel.IsChecking) && !_viewModel.IsChecking && _viewModel.HasResult)
            {
                Dispatcher.BeginInvoke(new Action(() => ResultPanel.BringIntoView()), DispatcherPriority.Loaded);
            }
        }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            // Справа у верхнего края рабочей области: слева остаётся редактор, и урок виден рядом с кодом.
            Rect area = SystemParameters.WorkArea;
            Left = Math.Max(area.Left, area.Right - Width - 16);
            Top = area.Top + 70;
        }

        private void OnResetProgress(object sender, RoutedEventArgs e)
        {
            MessageBoxResult answer = MessageBox.Show(this,
                "Сбросить прогресс курса? Пройденные уроки и звёзды будут потеряны.",
                "Курс по Glass", MessageBoxButton.YesNo, MessageBoxImage.Question, MessageBoxResult.No);
            if (answer == MessageBoxResult.Yes) _viewModel.ResetProgressCommand.Execute(null);
        }
    }
}
