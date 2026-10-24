"""Record reviewed originals; metadata only, no changes to source PDFs."""
import json
from pathlib import Path

root = Path(__file__).resolve().parent.parent
path = root / 'content/books.json'
books = json.loads(path.read_text(encoding='utf-8'))
new_books = [
    dict(id='qurra-biographies', title='تراجم القراء', author='فائز عبد القادر شيخ الزور', subject='salaf',
         description='النسخة الإلكترونية الكاملة المتاحة من دار الإسلام؛ تضم تراجم أئمة القراءة ورُواتهم. مدخل إلى تراجم السلف، وليست موسوعة شاملة لجميع الأئمة.',
         topics=['تراجم السلف', 'القراء', 'نافع', 'عاصم', 'ابن كثير', 'أبو عمرو', 'الكسائي', 'العلماء'],
         source='https://islamhouse.com/ar/books/810341/',
         downloadUrl='https://d1.islamhouse.com/data/ar/ih_books/single9/ar_tragm_AlQra.pdf',
         inputFile='salaf.pdf', asset='books/qurra-biographies.pdf', pages=40,
         publisher='المصدر الرقمي: دار الإسلام', edition='النسخة الإلكترونية المنشورة في 4 فبراير 2015؛ لا تظهر بيانات طبعة ورقية مفصلة',
         sha256='a54b14fff425ef5b17ce1f4a1c6a03884a7beb97631fe1baef4f341e6cb5ee62'),
    dict(id='hearts-remedies', title='أمراض القلوب وشفاؤها', author='عبد الله بن جار الله بن إبراهيم الجار الله', subject='tazkiyah',
         description='النسخة الإلكترونية الكاملة المتاحة من دار الإسلام في أمراض القلوب وتهذيب النفس، مع حفظ النص والصفحات كما نشرها المصدر.',
         topics=['التزكية', 'تزكية النفس', 'القلب', 'الأخلاق', 'الذكر', 'المعاصي', 'الإخلاص'],
         source='https://islamhouse.com/ar/books/209148/',
         downloadUrl='https://d1.islamhouse.com/data/ar/ih_books/single2/ar_hearts_and_to_heal_diseases.pdf',
         inputFile='tazkiyah-hearts.pdf', asset='books/hearts-remedies.pdf', pages=41,
         publisher='المصدر الرقمي: دار الإسلام', edition='النسخة الإلكترونية المرفقة بالمصدر؛ ترقيم صفحات الملف يختلف عن الترقيم المطبوع',
         sha256='bb13dc40c305973e07e0d0e815be7c0a2adb484c951390a7fff58712640c8eb2'),
]
for book in new_books:
    book.update(licenseName='إذن نشر غير تجاري مع نسبة المصدر وحفظ المحتوى',
                licenseUrl='https://d1.islamhouse.com/html/faq.htm',
                rightsNote='وفق إذن النشر في الأسئلة المتكررة لدار الإسلام، مع نسبة المصدر وعدم تغيير المحتوى. لا يُفترض إذن للاستغلال التجاري لمواد الجهات الأخرى؛ ملف الكتاب محفوظ دون تعديل.')
    books = [b for b in books if b['id'] != book['id']]
    books.append(book)
path.write_text(json.dumps(books, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
