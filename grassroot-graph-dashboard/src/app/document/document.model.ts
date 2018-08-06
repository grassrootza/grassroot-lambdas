export class Document {
    public constructor(public docType: string,
        public machineName: string,
        public humanName: string,
        public procedures: string[],
        public issues: string[],
        public problems: string[],
        public mainText?: string,
        public s3bucket?: string,
        public s3string?: string) { }
}

export const transformDoc = (doc: Document): Document => {
    return new Document(doc.docType, doc.machineName, doc.humanName, doc.procedures, doc.issues, doc.problems, 
        doc.mainText, doc.s3bucket, doc.s3string);
};
